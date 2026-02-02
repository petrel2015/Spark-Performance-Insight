<template>
  <div class="stage-dag-container">
    <div ref="graphContainer" class="graph-container"></div>
    <div v-if="!hasRddInfo" class="no-data-msg">
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

  graph = new Graph({
    container: graphContainer.value,
    autoResize: true,
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
  await nextTick();
  if (!graph) initGraph();
  if (!hasRddInfo.value) {
    if (graph) graph.clearCells();
    return;
  }

  try {
    const rddInfos = JSON.parse(props.stage.rddInfo);
    if (!Array.isArray(rddInfos) || rddInfos.length === 0) return;

    const scopeMap = new Map();
    const nodes = [];
    const edges = [];
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
        body: { fill: 'rgba(240, 240, 240, 0.4)', stroke: '#ccc', strokeWidth: 1, strokeDasharray: '5 5', rx: 4, ry: 4 },
        label: { text: s.label, refY: -15, fontSize: 10, fill: '#999', fontWeight: 'bold' }
      },
      zIndex: 0
    }));

    const rddNodesData = rddNodes.map(r => ({
      id: r.id, shape: 'rect', width: 220, height: 80, parent: r.parentId, data: { rddId: r.rddId },
      attrs: {
        body: { fill: '#ffffff', stroke: '#1565c0', strokeWidth: 1, rx: 6, ry: 6 },
        label: { text: r.label, fill: '#333', fontSize: 11, textWrap: { width: 200, height: 70, ellipsis: true } }
      },
      zIndex: 10
    }));

    const allNodes = [...scopeNodesData, ...rddNodesData];

    rddInfos.forEach(rdd => {
      const rddId = rdd['RDD ID'];
      const parents = rdd['Parent IDs'];
      if (Array.isArray(parents)) {
        parents.forEach(parentId => {
          if (rddInfos.find(r => r['RDD ID'] === parentId)) {
            edges.push({
              source: `rdd-${parentId}`, target: `rdd-${rddId}`,
              attrs: { line: { stroke: '#A2B1C3', strokeWidth: 1, targetMarker: 'classic' } },
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

    // --- Convert to X6 Nodes (Flat Hierarchy for Stability) ---
    const finalNodes = allNodes.map(n => {
      const dagreNode = g.node(n.id);
      return {
        ...n,
        // Use absolute coordinates from Dagre directly
        x: dagreNode.x - dagreNode.width / 2,
        y: dagreNode.y - dagreNode.height / 2,
        width: dagreNode.width,
        height: dagreNode.height,
        // Remove X6 parent linkage to avoid coordinate system confusion. 
        // We rely on Dagre's absolute layout and zIndex for visual nesting.
        parent: undefined 
      };
    });

    graph.fromJSON({ nodes: finalNodes, edges });
    graph.zoomToFit({ padding: 40, maxScale: 1 });
    updateGraphInteraction();

  } catch (err) {
    console.error("Failed to parse or render RDD DAG", err);
  }
};

onMounted(() => {
  renderDAG();
  resizeObserver = new ResizeObserver(() => {
    if (graph && graphContainer.value) {
      const width = graphContainer.value.offsetWidth;
      if (width > 0) {
        graph.resize(width, 600);
        graph.zoomToFit({ padding: 40, maxScale: 1 });
      }
    }
  });
  if (graphContainer.value) resizeObserver.observe(graphContainer.value.parentElement);
});

watch(() => props.stage, () => {
  renderDAG();
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
</style>