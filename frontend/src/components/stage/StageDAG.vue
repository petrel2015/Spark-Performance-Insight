<template>
  <div class="stage-dag-container">
    <div ref="graphContainer" class="graph-container"></div>
    <div v-if="isLoading" class="loading-overlay">
      <div class="spinner"></div>
      <span>Loading DAG...</span>
    </div>
    <div v-if="!isLoading && !hasRddInfo" class="no-data-msg">
      No RDD DAG information available for this stage.
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, computed, nextTick } from 'vue';
import { Graph } from '@antv/x6';
import * as dagre from 'dagre';

const props = defineProps({
  stage: { type: Object, required: true }
});

const graphContainer = ref(null);
const isZoomLocked = ref(true); // Default locked
const isLoading = ref(false);
let graph = null;
let resizeObserver = null;

defineExpose({
  isZoomLocked,
  toggleZoomLock: () => toggleZoomLock()
});

const hasRddInfo = computed(() => {
  return props.stage && props.stage.rddInfo;
});

const initGraph = () => {
  if (!graphContainer.value) return;
  if (graph) graph.dispose();

  graph = new Graph({
    container: graphContainer.value,
    width: graphContainer.value.offsetWidth,
    height: 600, // Explicit height matching CSS
    background: { color: '#f8f9fa' },
    panning: !isZoomLocked.value,
    mousewheel: !isZoomLocked.value,
    grid: true,
    connecting: {
      router: 'manhattan',
      connector: { name: 'rounded' },
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

const renderDAG = async () => {
  // Wait for container to be layouted
  await nextTick();
  if (!graphContainer.value || graphContainer.value.offsetWidth === 0) {
    isLoading.value = false;
    return;
  }

  // Always re-init to ensure fresh state on re-open
  initGraph();

  if (!hasRddInfo.value) {
    isLoading.value = false;
    return;
  }

  try {
    const rddInfos = JSON.parse(props.stage.rddInfo);
    if (!Array.isArray(rddInfos) || rddInfos.length === 0) {
      isLoading.value = false;
      return;
    }

    // --- 1. Parse Scopes and RDDs ---
    const scopeMap = new Map();
    const rddNodes = [];

    const parseScope = (scopeStr) => {
      if (!scopeStr) return null;
      try { return JSON.parse(scopeStr); } catch (e) { return null; }
    };

    const getOrCreateScope = (scopeObj) => {
      if (!scopeObj) return null;
      const id = scopeObj.id;
      if (scopeMap.has(id)) return scopeMap.get(id);
      let parentId = null;
      if (scopeObj.parent) {
        const p = getOrCreateScope(scopeObj.parent);
        if (p) parentId = p.id;
      }
      const scopeNode = { id: `scope-${id}`, label: scopeObj.name, parentId: parentId ? `scope-${parentId}` : null };
      scopeMap.set(id, scopeNode);
      return scopeNode;
    };

    rddInfos.forEach(rdd => {
      const rddId = rdd['RDD ID'];
      const name = rdd['Name'];
      const callSite = rdd['Callsite'] || '';
      const scopeStr = rdd['Scope'];
      let parentScopeId = null;
      if (scopeStr) {
        const scopeObj = parseScope(scopeStr);
        const s = getOrCreateScope(scopeObj);
        if (s) parentScopeId = s.id;
      }
      rddNodes.push({ id: `rdd-${rddId}`, rddId, label: `[${rddId}] ${name}
${callSite}`, parentId: parentScopeId });
    });

    const scopeNodesData = Array.from(scopeMap.values()).map(s => ({
      id: s.id, shape: 'rect', width: 100, height: 100, parent: s.parentId,
      attrs: {
        body: { fill: 'rgba(160, 223, 255, 1)', stroke: '#3EC0FF', strokeWidth: 1, rx: 4, ry: 4 },
        label: { text: s.label, refY: 15, fontSize: 10, fill: '#333', fontWeight: 'bold' }
      },
      zIndex: 0
    }));

    const rddNodesData = rddNodes.map(r => ({
      id: r.id, shape: 'rect', width: 220, height: 80, parent: r.parentId, data: { rddId: r.rddId },
      attrs: {
        body: { fill: '#C3EBFF', stroke: '#3EC0FF', strokeWidth: 1, rx: 6, ry: 6},
        label: { text: r.label, fill: '#333', fontSize: 11, textWrap: { width: 200, height: 70, ellipsis: true } }
      },
      zIndex: 10
    }));

    const allNodes = [...scopeNodesData, ...rddNodesData];
    const edges = []; // Define edges before usage

    rddInfos.forEach(rdd => {
      const rddId = rdd['RDD ID'];
      const parents = rdd['Parent IDs'];
      if (Array.isArray(parents)) {
        parents.forEach(parentId => {
          if (rddInfos.find(r => r['RDD ID'] === parentId)) {
            edges.push({
              source: `rdd-${parentId}`, target: `rdd-${rddId}`,
              attrs: { line: { stroke: '#444', strokeWidth: 1, targetMarker: 'classic' } },
              zIndex: 20
            });
          }
        });
      }
    });

    // --- Layout using Dagre ---
    // Ensure graphlib exists (handle different import styles)
    const GraphLib = dagre.graphlib ? dagre.graphlib.Graph : dagre.Graph;
    if (!GraphLib) {
        console.error("StageDAG: dagre.graphlib is undefined", dagre);
        isLoading.value = false;
        return;
    }

    const g = new GraphLib({ compound: true });
    g.setGraph({ rankdir: 'LR', nodesep: 40, ranksep: 60 });
    g.setDefaultEdgeLabel(() => ({}));
    
    allNodes.forEach(n => {
      g.setNode(n.id, { width: n.width, height: n.height, label: n.id });
      if (n.parent) g.setParent(n.id, n.parent);
    });
    edges.forEach(e => g.setEdge(e.source, e.target));
    dagre.layout(g);

    // --- Convert Absolute to Relative Coordinates ---
    const finalNodes = allNodes.map(n => {
      const dagreNode = g.node(n.id);
      return {
        ...n,
        // Use absolute coordinates from Dagre directly
        x: dagreNode.x - dagreNode.width / 2,
        y: dagreNode.y - dagreNode.height / 2,
        width: dagreNode.width,
        height: dagreNode.height,
        parent: undefined 
      };
    });

    graph.fromJSON({ nodes: finalNodes, edges });
    
    // Explicitly update size and zoom
    if (graphContainer.value) {
        graph.resize(graphContainer.value.offsetWidth, 600);
    }
    
    setTimeout(() => {
        graph.zoomToFit({ padding: 40, maxScale: 1 });
        updateGraphInteraction();
        isLoading.value = false;
    }, 100);

  } catch (err) {
    console.error("Failed to parse or render RDD DAG", err);
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
  // Use ResizeObserver to trigger initial render when visible
  resizeObserver = new ResizeObserver(() => {
    if (graphContainer.value && graphContainer.value.offsetWidth > 0) {
       triggerReload();
    }
  });
  if (graphContainer.value) resizeObserver.observe(graphContainer.value.parentElement);
});

watch(() => props.stage, () => {
  triggerReload();
}, { deep: true });

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (graph) graph.dispose();
});
</script>

<style scoped>
.stage-dag-container {
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
  background: rgba(255, 255, 255, 0.8);
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
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>