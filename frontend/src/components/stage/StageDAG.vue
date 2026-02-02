<template>
  <div class="stage-dag-container">
    <div ref="graphContainer" class="graph-container"></div>
    <div v-if="!hasRddInfo" class="no-data-msg">
      No RDD DAG information available for this stage.
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, computed } from 'vue';
import { Graph } from '@antv/x6';
import dagre from 'dagre';

const props = defineProps({
  stage: { type: Object, required: true }
});

const graphContainer = ref(null);
let graph = null;

const hasRddInfo = computed(() => {
  return props.stage && props.stage.rddInfo;
});

const initGraph = () => {
  if (!graphContainer.value) return;

  graph = new Graph({
    container: graphContainer.value,
    width: graphContainer.value.offsetWidth || 800,
    height: 600,
    background: { color: '#f8f9fa' },
    panning: true,
    mousewheel: true,
    grid: true,
    connecting: {
      router: 'manhattan',
      connector: { name: 'rounded' },
      anchor: 'center',
      connectionPoint: 'boundary',
      dangling: false,
    },
    interacting: {
      nodeMovable: true,
    }
  });
};

const renderDAG = () => {
  if (!graph) initGraph();
  if (!hasRddInfo.value) {
    if (graph) graph.clearCells();
    return;
  }

  try {
    const rddInfos = JSON.parse(props.stage.rddInfo);
    if (!Array.isArray(rddInfos) || rddInfos.length === 0) return;

    // --- 1. Parse Scopes and RDDs ---
    const scopeMap = new Map(); // scopeId -> scopeNodeData
    const nodes = [];
    const edges = [];
    const rddNodes = [];

    // Helper to parse scope string
    const parseScope = (scopeStr) => {
      if (!scopeStr) return null;
      try {
        return JSON.parse(scopeStr);
      } catch (e) {
        return null;
      }
    };

    // Helper to get or create scope node
    const getOrCreateScope = (scopeObj) => {
      if (!scopeObj) return null;
      const id = scopeObj.id;
      if (scopeMap.has(id)) return scopeMap.get(id);

      // Process parent recursively
      let parentId = null;
      if (scopeObj.parent) {
        const p = getOrCreateScope(scopeObj.parent);
        if (p) parentId = p.id;
      }

      const scopeNode = {
        id: `scope-${id}`,
        label: scopeObj.name,
        parentId: parentId ? `scope-${parentId}` : null,
        children: []
      };
      
      scopeMap.set(id, scopeNode);
      return scopeNode;
    };

    // First pass: Create RDD nodes and identify Scopes
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

      rddNodes.push({
        id: `rdd-${rddId}`,
        rddId,
        label: `[${rddId}] ${name}\n${callSite}`,
        parentId: parentScopeId
      });
    });

    // Create Scope Nodes for X6
    const scopeNodes = Array.from(scopeMap.values()).map(s => ({
      id: s.id,
      shape: 'rect',
      // Initial size, layout will adjust
      width: 100, 
      height: 100,
      parent: s.parentId, // X6 parent nesting
      attrs: {
        body: {
          fill: 'rgba(240, 240, 240, 0.5)',
          stroke: '#999',
          strokeWidth: 1,
          strokeDasharray: '5 5',
          rx: 4,
          ry: 4,
        },
        label: {
          text: s.label,
          refY: -15, // Label above the box
          fontSize: 10,
          fill: '#666',
          fontWeight: 'bold'
        }
      },
      zIndex: 0
    }));

    // Create RDD Nodes for X6
    const finalRddNodes = rddNodes.map(r => ({
      id: r.id,
      shape: 'rect',
      width: 220,
      height: 80,
      parent: r.parentId,
      data: { rddId: r.rddId },
      attrs: {
        body: {
          fill: '#e3f2fd',
          stroke: '#1565c0',
          strokeWidth: 1,
          rx: 6,
          ry: 6,
        },
        label: {
          text: r.label,
          fill: '#333',
          fontSize: 12,
          textWrap: {
            width: 200,
            height: 70,
            ellipsis: true
          }
        }
      },
      zIndex: 10
    }));

    nodes.push(...scopeNodes, ...finalRddNodes);

    // Build Edges
    rddInfos.forEach(rdd => {
      const rddId = rdd['RDD ID'];
      const parents = rdd['Parent IDs'];
      if (Array.isArray(parents)) {
        parents.forEach(parentId => {
          if (rddInfos.find(r => r['RDD ID'] === parentId)) {
            edges.push({
              source: `rdd-${parentId}`,
              target: `rdd-${rddId}`,
              attrs: {
                line: {
                  stroke: '#A2B1C3',
                  strokeWidth: 1,
                  targetMarker: 'classic',
                },
              },
              zIndex: 20
            });
          }
        });
      }
    });

    // --- 2. Layout using Dagre ---
    // Dagre needs a graph structure.
    const g = new dagre.graphlib.Graph({ compound: true });
    g.setGraph({ rankdir: 'LR', nodesep: 50, ranksep: 80 });
    g.setDefaultEdgeLabel(() => ({}));

    // Add nodes to dagre
    nodes.forEach(n => {
      g.setNode(n.id, { width: n.width, height: n.height, label: n.id });
      if (n.parent) {
        g.setParent(n.id, n.parent);
      }
    });

    // Add edges to dagre
    edges.forEach(e => {
      g.setEdge(e.source, e.target);
    });

    // Calculate layout
    dagre.layout(g);

    // Apply positions back to X6 nodes
    const x6Nodes = [];
    nodes.forEach(n => {
      const dagreNode = g.node(n.id);
      if (dagreNode) {
        x6Nodes.push({
          ...n,
          x: dagreNode.x - dagreNode.width / 2,
          y: dagreNode.y - dagreNode.height / 2,
          width: dagreNode.width,
          height: dagreNode.height
        });
      }
    });

    graph.fromJSON({ nodes: x6Nodes, edges });
    graph.zoomToFit({ padding: 20 });

  } catch (err) {
    console.error("Failed to parse or render RDD DAG", err);
  }
};

onMounted(() => {
  renderDAG();
});

watch(() => props.stage, () => {
  renderDAG();
}, { deep: true });

onBeforeUnmount(() => {
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
