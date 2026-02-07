<template>
  <div class="job-dag-container">
    <div ref="graphContainer" class="graph-container"></div>
    <div v-if="isLoading" class="loading-overlay">
      <div class="spinner"></div>
      <span>Loading Job DAG...</span>
    </div>
    <div v-if="!isLoading && (!stages || stages.length === 0)" class="no-data-msg">
      No Stage information available for this job.
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import {Graph} from '@antv/x6';
import * as dagre from 'dagre';
import {getJobStages} from '../../api';

const props = defineProps({
  appId: {type: String, required: true},
  jobId: {type: Number, required: true}
});

const stages = ref([]);
const graphContainer = ref(null);
const isZoomLocked = ref(true);
const isLoading = ref(false);
let graph = null;
let resizeObserver = null;

defineExpose({
  isZoomLocked,
  toggleZoomLock: () => toggleZoomLock()
});

const initGraph = () => {
  if (!graphContainer.value) return;
  if (graph) graph.dispose();

  graph = new Graph({
    container: graphContainer.value,
    width: graphContainer.value.offsetWidth,
    height: graphContainer.value.offsetHeight || 600,
    background: {color: '#f8f9fa'},
    panning: !isZoomLocked.value,
    mousewheel: !isZoomLocked.value,
    grid: true,
    connecting: {
      router: 'manhattan',
      connector: {name: 'rounded'},
      anchor: 'center',
      connectionPoint: 'boundary',
      dangling: false,
    },
    interacting: {
      nodeMovable: !isZoomLocked.value,
    }
  });
};

const updateGraphInteraction = () => {
  if (!graph) return;
  if (isZoomLocked.value) {
    graph.disablePanning();
    graph.disableMouseWheel();
  } else {
    graph.enablePanning();
    graph.enableMouseWheel();
  }
};

const toggleZoomLock = () => {
  isZoomLocked.value = !isZoomLocked.value;
  updateGraphInteraction();
};

const fetchStages = async () => {
  try {
    const res = await getJobStages(props.appId, props.jobId);
    stages.value = res.data || [];
    triggerReload();
  } catch (err) {
    console.error("Failed to fetch job stages for DAG", err);
    stages.value = [];
    isLoading.value = false;
  }
};

const renderDAG = async () => {
  await nextTick();
  if (!graphContainer.value || graphContainer.value.offsetWidth === 0) {
    isLoading.value = false;
    return;
  }

  initGraph();

  if (!stages.value || stages.value.length === 0) {
    isLoading.value = false;
    return;
  }

  try {
    const allNodes = [];
    const edges = [];
    const rddIdToStageId = new Map();
    const processedRdds = new Set();
    const scopeMap = new Map(); // Global scope map across stages

    // 1. Process Stages (Clusters) and RDDs
    // Sort stages by ID to ensure order (optional)
    const sortedStages = [...stages.value].sort((a, b) => a.stageId - b.stageId);

    const parseScope = (scopeStr) => {
      if (!scopeStr) return null;
      try {
        return JSON.parse(scopeStr);
      } catch (e) {
        return null;
      }
    };

    const getOrCreateScope = (scopeObj, stageGroupId) => {
      if (!scopeObj) return null;
      const globalScopeId = `${stageGroupId}-scope-${scopeObj.id}`;
      if (scopeMap.has(globalScopeId)) return scopeMap.get(globalScopeId);
      
      let parentId = stageGroupId; // Default parent is the Stage
      if (scopeObj.parent) {
        const p = getOrCreateScope(scopeObj.parent, stageGroupId);
        if (p) parentId = p.id;
      }
      
      const scopeNode = {
        id: globalScopeId, 
        label: scopeObj.name, 
        parent: parentId,
        shape: 'rect',
        width: 100, // Placeholder
        height: 100,
        zIndex: 1,
        attrs: {
          body: {fill: 'rgba(160, 223, 255, 1)', stroke: '#3EC0FF', strokeWidth: 1, strokeDasharray: '5 5', rx: 4, ry: 4},
          label: {text: scopeObj.name, refY: 15, fontSize: 10, fill: '#333', fontWeight: 'bold'}
        }
      };
      scopeMap.set(globalScopeId, scopeNode);
      allNodes.push(scopeNode);
      return scopeNode;
    };

    // Prepare Stage Groups
    sortedStages.forEach(stage => {
      const stageGroupId = `stage-${stage.stageId}`;
      allNodes.push({
        id: stageGroupId,
        shape: 'rect',
        width: 300, 
        height: 200,
        label: `Stage ${stage.stageId}`,
        zIndex: 0,
        attrs: {
          body: {fill: 'rgba(255, 235, 238, 0.5)', stroke: '#FFCDD2', strokeDasharray: '5 5', strokeWidth: 1, rx: 4, ry: 4},
          label: {
            text: `Stage ${stage.stageId}`,
            refY: 12,
            fontSize: 10,
            fontWeight: 'bold',
            fill: '#333',
            textWrap: {width: 280, ellipsis: true}
          }
        }
      });

      if (stage.rddInfo) {
        try {
          const rddInfos = JSON.parse(stage.rddInfo);
          rddInfos.forEach(rdd => {
            const rddId = rdd['RDD ID'];
            if (!processedRdds.has(rddId)) {
              processedRdds.add(rddId);
              
              let parentNodeId = stageGroupId;
              const scopeStr = rdd['Scope'];
              if (scopeStr) {
                const scopeObj = parseScope(scopeStr);
                const s = getOrCreateScope(scopeObj, stageGroupId);
                if (s) parentNodeId = s.id;
              }

              allNodes.push({
                id: `rdd-${rddId}`,
                parent: parentNodeId,
                shape: 'rect',
                width: 200,
                height: 60,
                label: `[${rddId}] ${rdd.Name}`,
                zIndex: 10,
                attrs: {
                  body: {fill: '#C3EBFF', stroke: '#3EC0FF', strokeWidth: 1, rx: 6, ry: 6},
                  label: {text: `[${rddId}] ${rdd.Name}`, fontSize: 11, textWrap: {width: 180, ellipsis: true}, fill: '#333'}
                }
              });
            }

            // Edges
            const parents = rdd['Parent IDs'];
            if (Array.isArray(parents)) {
              parents.forEach(parentId => {
                edges.push({
                  source: `rdd-${parentId}`,
                  target: `rdd-${rddId}`,
                  attrs: {line: {stroke: '#444', strokeWidth: 1, targetMarker: 'classic'}},
                  zIndex: 20
                });
              });
            }
          });
        } catch (e) {
          console.warn("Failed to parse rddInfo for stage " + stage.stageId, e);
        }
      }
    });

    // --- Layout using Dagre ---
    const GraphLib = dagre.graphlib ? dagre.graphlib.Graph : dagre.Graph;
    if (!GraphLib) {
      isLoading.value = false;
      return;
    }

    const g = new GraphLib({compound: true});
    g.setGraph({rankdir: 'LR', nodesep: 50, ranksep: 80, marginx: 30, marginy: 100});
    g.setDefaultEdgeLabel(() => ({}));

    // Add nodes to dagre
    allNodes.forEach(n => {
      g.setNode(n.id, {width: n.width, height: n.height, label: n.id});
      if (n.parent) {
        g.setParent(n.id, n.parent);
      }
    });

    // Add edges to dagre
    // Filter edges: ensure both source and target nodes exist
    const validEdges = edges.filter(e => g.hasNode(e.source) && g.hasNode(e.target));
    validEdges.forEach(e => g.setEdge(e.source, e.target));

    // Connect Stages if explicit parentStageIds exist (fallback for missing RDD links)
    sortedStages.forEach(stage => {
      if (stage.parentStageIds) {
        const pIds = stage.parentStageIds.split(',').map(s => s.trim());
        pIds.forEach(pId => {
          const sourceGroup = `stage-${pId}`;
          const targetGroup = `stage-${stage.stageId}`;
          // Only add edge if RDD connection doesn't already cover it (heuristic)
          // Actually, edges between groups in Dagre are tricky. Dagre usually routes edges between nodes *inside* groups.
          // If we add edge between groups, Dagre might handle it.
          // Let's rely on RDD edges primarily. If RDD edges are missing, the stages will be disconnected visually.
          // We can add "dummy" edges between groups if needed, but X6/Dagre might not render group-to-group edges well mixed with node-to-node.
          // Let's stick to RDD edges.
        });
      }
    });

    dagre.layout(g);

    // Convert back to X6 using absolute positions and FLATTEN the hierarchy
    // (X6 nesting can be tricky with coordinates, flattening matches StageDAG's approach)
    const finalNodes = allNodes.map(n => {
      const dagreNode = g.node(n.id);
      return {
        ...n,
        x: dagreNode.x - dagreNode.width / 2,
        y: dagreNode.y - dagreNode.height / 2,
        width: dagreNode.width,
        height: dagreNode.height,
        parent: undefined // Flatten for reliable positioning
      };
    });

    graph.fromJSON({nodes: finalNodes, edges: validEdges});

    // Explicitly update size and zoom
    if (graphContainer.value) {
      graph.resize(graphContainer.value.offsetWidth, graphContainer.value.offsetHeight || 600);
    }

    setTimeout(() => {
      graph.zoomToFit({padding: 40, maxScale: 1});
      updateGraphInteraction();
      isLoading.value = false;
    }, 100);

  } catch (err) {
    console.error("Failed to render Job DAG", err);
    isLoading.value = false;
  }
};

const triggerReload = () => {
  isLoading.value = true;
  if (resizeObserver._timer) clearTimeout(resizeObserver._timer);
  resizeObserver._timer = setTimeout(() => {
    renderDAG();
  }, 10);
};

onMounted(() => {
  fetchStages();
  resizeObserver = new ResizeObserver(() => {
    if (graphContainer.value && graphContainer.value.offsetWidth > 0) {
      triggerReload();
    }
  });
  if (graphContainer.value) resizeObserver.observe(graphContainer.value.parentElement);
});

watch(() => props.jobId, fetchStages);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (graph) graph.dispose();
});
</script>

<style scoped>
.job-dag-container {
  width: 100%;
  border: 1px solid #eee;
  border-radius: 8px;
  background: white;
  position: relative;
  overflow: hidden;
  min-height: 200px;
}

.graph-container {
  width: 100%;
  height: 600px;
  min-height: 400px;
}

.no-data-msg {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #999;
  font-style: italic;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 10px;
  z-index: 50;
  color: #666;
  font-size: 0.9rem;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #f3f3f3;
  border-top: 3px solid #3498db;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style>
