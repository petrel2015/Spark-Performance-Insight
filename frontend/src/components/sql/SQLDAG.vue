<template>
  <div class="sql-dag-container">
    <div ref="graphContainer" class="graph-container"></div>
    <div v-if="isLoading" class="loading-overlay">
      <div class="spinner"></div>
      <span>Rendering SQL Plan...</span>
    </div>
    <div v-if="!isLoading && !planInfo" class="no-data-msg">
      No Visual Plan information available.
    </div>
  </div>
</template>

<script setup>
import {ref, onMounted, watch, onBeforeUnmount, nextTick} from 'vue';
import {Graph} from '@antv/x6';
import * as dagre from 'dagre';

const props = defineProps({
  planInfo: {type: Object, default: null},
});

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

const renderDAG = async () => {
  await nextTick();
  if (!graphContainer.value || graphContainer.value.offsetWidth === 0) {
    isLoading.value = false;
    return;
  }

  initGraph();

  if (!props.planInfo) {
    isLoading.value = false;
    return;
  }

  try {
    const allNodes = [];
    const edges = [];
    let nodeIdCounter = 0;

    // Recursive function to traverse SparkPlanInfo tree
    const traverse = (node, parentId = null) => {
      const currentId = `node-${nodeIdCounter++}`;
      
      // Node Label Processing
      let label = node.nodeName;
      // Extract main operation name (e.g., "Scan Parquet") from simpleString if needed
      // But nodeName is usually good enough.
      
      allNodes.push({
        id: currentId,
        shape: 'rect',
        width: 180,
        height: 60,
        label: label,
        data: node, // Store full node info for potential tooltips
        attrs: {
          body: {
            fill: '#E3F2FD', 
            stroke: '#2196F3', 
            strokeWidth: 1, 
            rx: 6, 
            ry: 6
          },
          label: {
            text: label,
            fill: '#333',
            fontSize: 12,
            fontWeight: 'bold',
            textWrap: {width: 160, ellipsis: true}
          }
        }
      });

      if (parentId) {
        // Data flow direction: Child -> Parent
        // This puts leaves (sources) at the top and root (result) at the bottom with TB rankdir
        edges.push({
          source: currentId,
          target: parentId,
          attrs: {
            line: {
              stroke: '#999', 
              strokeWidth: 1,
              targetMarker: 'classic' // Add arrow head to show flow
            }
          }
        });
      }

      if (node.children && node.children.length > 0) {
        node.children.forEach(child => traverse(child, currentId));
      }
    };

    traverse(props.planInfo);

    // Layout using Dagre
    const GraphLib = dagre.graphlib ? dagre.graphlib.Graph : dagre.Graph;
    if (!GraphLib) return;

    const g = new GraphLib();
    // BT: Bottom-Top (Data flows up). 
    // Spark Plan Root is final result. Scan is leaf.
    // If we want Scan at Top, we use TB and reverse edges? 
    // Usually plans are shown Root at Top. So TB is fine.
    g.setGraph({rankdir: 'TB', nodesep: 50, ranksep: 50, marginx: 20, marginy: 20});
    g.setDefaultEdgeLabel(() => ({}));

    allNodes.forEach(n => g.setNode(n.id, {width: n.width, height: n.height}));
    edges.forEach(e => g.setEdge(e.source, e.target));

    dagre.layout(g);

    const finalNodes = allNodes.map(n => {
      const dn = g.node(n.id);
      return {
        ...n,
        x: dn.x - dn.width / 2,
        y: dn.y - dn.height / 2
      };
    });

    graph.fromJSON({nodes: finalNodes, edges});

    if (graphContainer.value) {
      graph.resize(graphContainer.value.offsetWidth, graphContainer.value.offsetHeight || 600);
    }

    setTimeout(() => {
      graph.zoomToFit({padding: 20, maxScale: 1});
      updateGraphInteraction();
      isLoading.value = false;
    }, 100);

  } catch (err) {
    console.error("Failed to render SQL DAG", err);
    isLoading.value = false;
  }
};

const triggerReload = () => {
  isLoading.value = true;
  if (resizeObserver && resizeObserver._timer) clearTimeout(resizeObserver._timer);
  const timer = setTimeout(() => {
    renderDAG();
  }, 10);
  if (resizeObserver) resizeObserver._timer = timer;
};

onMounted(() => {
  if (props.planInfo) triggerReload();
  resizeObserver = new ResizeObserver(() => {
    if (graphContainer.value && graphContainer.value.offsetWidth > 0) {
      triggerReload();
    }
  });
  if (graphContainer.value) resizeObserver.observe(graphContainer.value.parentElement);
});

watch(() => props.planInfo, triggerReload);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (graph) graph.dispose();
});
</script>

<style scoped>
.sql-dag-container {
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
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>
