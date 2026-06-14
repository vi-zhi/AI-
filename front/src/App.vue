<template>
  <div class="app">
    <!-- 顶部工具栏 -->
    <header class="toolbar">
      <div class="toolbar-left">
        <svg class="logo" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1677ff" stroke-width="2">
          <rect x="3" y="3" width="7" height="7" rx="1"/>
          <rect x="14" y="3" width="7" height="7" rx="1"/>
          <rect x="3" y="14" width="7" height="7" rx="1"/>
          <rect x="14" y="14" width="7" height="7" rx="1"/>
        </svg>
        <span class="app-name">AI 语音画图</span>
      </div>
      <div class="toolbar-right">
        <button class="btn-toolbar" :class="{ active: layout === 'side' }" @click="setLayout('side')" title="聊天侧边栏">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="15" y1="3" x2="15" y2="21"/>
          </svg>
        </button>
        <button class="btn-toolbar" :class="{ active: layout === 'bottom' }" @click="setLayout('bottom')" title="聊天底部">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <line x1="3" y1="15" x2="21" y2="15"/>
          </svg>
        </button>
        <button class="btn-toolbar" @click="handleReset" title="新对话">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path d="M3 3v5h5"/>
          </svg>
        </button>
      </div>
    </header>

    <!-- 主画布区 -->
    <div class="canvas-area" :class="layout">
      <!-- 流程图显示区域 -->
      <div class="drawio-canvas" ref="svgContainerRef">
        <div v-if="!hasXml" class="placeholder">
          <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="#ddd" stroke-width="1">
            <rect x="3" y="3" width="18" height="18" rx="2"/>
            <path d="M8 7h8M8 11h6M8 15h9"/>
          </svg>
          <p>在聊天面板输入需求，AI 将在此绘制流程图</p>
        </div>
      </div>

      <!-- 聊天面板 overlay -->
      <div v-if="layout === 'side'" class="chat-overlay-side">
        <ChatPanel @reset="handleReset" />
      </div>
      <div v-if="layout === 'bottom'" class="chat-overlay-bottom">
        <ChatPanel @reset="handleReset" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import ChatPanel from './components/ChatPanel.vue'
import { useChatStore } from '@/stores/chat'

const store = useChatStore()
const svgContainerRef = ref<HTMLElement>()

// 布局: side = 右侧聊天栏, bottom = 底部聊天栏
const layout = ref<'side' | 'bottom'>(
  (localStorage.getItem('draw-tool-layout') as 'side' | 'bottom') || 'side',
)

const hasXml = computed(() => !!store.currentXml)

function setLayout(value: 'side' | 'bottom') {
  layout.value = value
  localStorage.setItem('draw-tool-layout', value)
}

function handleReset() {
  store.reset()
}

/* ===== draw.io XML → SVG 渲染 ===== */

interface NodeInfo {
  x: number; y: number; w: number; h: number;
  fillColor: string; strokeColor: string;
  fontSize: number; fontFamily: string;
  rounded: boolean; shape: string;
  label: string;
}

function parseDrawioXml(xml: string): { nodes: NodeInfo[]; edges: EdgeInfo[] } {
  const parser = new DOMParser()
  const doc = parser.parseFromString(xml, 'text/xml')
  const cells = doc.getElementsByTagName('mxCell')
  const geometries = doc.getElementsByTagName('mxGeometry')

  // Build a map: index → geometry
  const geomMap = new Map<number, Element>()
  for (let i = 0; i < geometries.length; i++) {
    const parent = geometries[i].parentNode
    if (parent && parent.nodeType === Node.ELEMENT_NODE) {
      const cellEl = parent as Element
      // find the index of this parent cell in cells array
      const cellIdx = Array.from(cells).indexOf(cellEl)
      geomMap.set(cellIdx, geometries[i])
    }
  }

  const nodes: NodeInfo[] = []
  const edges: EdgeInfo[] = []

  for (let i = 0; i < cells.length; i++) {
    const cell = cells[i]
    const style = cell.getAttribute('style') || ''
    const vertex = cell.getAttribute('vertex')
    const edge = cell.getAttribute('edge')
    const value = (cell.getAttribute('value') || '').trim()

    if (vertex === '1' && style) {
      // Parse geometry
      const geom = geomMap.get(i)
      if (!geom) continue

      const x = parseInt(geom.getAttribute('x') || '0')
      const y = parseInt(geom.getAttribute('y') || '0')
      const w = parseInt(geom.getAttribute('width') || '0')
      const h = parseInt(geom.getAttribute('height') || '0')
      if (w <= 0 || h <= 0) continue

      // Parse style
      let fillColor = '#ffffff'
      let strokeColor = '#4d4d4d'
      let fontSize = 12
      let fontFamily = 'Arial, sans-serif'
      let rounded = style.includes('rounded=1')
      let shape = 'roundRect'

      const stylePairs = style.split(';')
      for (const pair of stylePairs) {
        const eqIdx = pair.indexOf('=')
        if (eqIdx < 0) continue
        const key = pair.substring(0, eqIdx).trim()
        const val = pair.substring(eqIdx + 1).trim()
        switch (key) {
          case 'fillColor': fillColor = val; break
          case 'strokeColor': strokeColor = val; break
          case 'fontSize': fontSize = parseInt(val) || 12; break
          case 'fontFamily': fontFamily = val; break
          case 'shape': shape = val; break
        }
      }

      let shapeType = shape
      if (shape === 'rhombus') shapeType = 'rhombus'
      else if (shape === 'ellipse' || shape === 'ellipse') shapeType = 'ellipse'
      else if (shape === 'hexagon') shapeType = 'hexagon'
      else shapeType = 'roundRect'

      nodes.push({ x, y, w, h, fillColor, strokeColor, fontSize, fontFamily, rounded, shape: shapeType, label: value })
    } else if (edge === '1') {
      // Parse edge (connection)
      const source = cell.getAttribute('source') || ''
      const target = cell.getAttribute('target') || ''
      if (source && target) {
        const label = (cell.getAttribute('value') || '').trim()
        edges.push({ source: source.replace('&amp;', '&').replace('#', ''), target: target.replace('&amp;', '&').replace('#', ''), label })
      }
    }
  }

  return { nodes, edges }
}

interface EdgeInfo {
  source: string; target: string; label: string
}

function generateSvg(nodes: NodeInfo[], edges: EdgeInfo[]): string {
  if (nodes.length === 0) return ''

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  for (const n of nodes) {
    minX = Math.min(minX, n.x)
    minY = Math.min(minY, n.y)
    maxX = Math.max(maxX, n.x + n.w)
    maxY = Math.max(maxY, n.y + n.h)
  }

  const pad = 40
  const svgW = maxX - minX + pad * 2
  const svgH = maxY - minY + pad * 2
  const ox = minX - pad
  const oy = minY - pad

  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${svgW}" height="${svgH}" viewBox="0 0 ${svgW} ${svgH}">`
  svg += `<rect width="100%" height="100%" fill="#fafafa"/>`

  // Draw edges (simple lines)
  const nodeMap = new Map<string, NodeInfo>()
  for (const n of nodes) {
    // Create a deterministic key for each node
    const key = `${n.x},${n.y},${n.w},${n.h}`
    nodeMap.set(key, n)
  }

  for (const edge of edges) {
    // Try to find source/target nodes by matching geometry indices
    // draw.io uses source/target references by cell index
    const edgeIdx = parseInt(edge.source.replace('#', '')) - 1
    const targetIdx = parseInt(edge.target.replace('#', '')) - 1
    const srcNode = nodes[edgeIdx] || nodes[0]
    const tgtNode = nodes[targetIdx] || nodes[nodes.length - 1]
    if (srcNode && tgtNode) {
      const sx = srcNode.x - ox + srcNode.w / 2
      const sy = srcNode.y - oy + srcNode.h / 2
      const tx = tgtNode.x - ox + tgtNode.w / 2
      const ty = tgtNode.y - oy + tgtNode.h / 2
      svg += `<line x1="${sx}" y1="${sy}" x2="${tx}" y2="${ty}" stroke="#999" stroke-width="1.5" marker-end="url(#arrow)"/>`
    }
  }

  // Arrow marker
  svg += `<defs><marker id="arrow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#999"/></marker></defs>`

  // Draw nodes
  for (const n of nodes) {
    const nx = n.x - ox
    const ny = n.y - oy

    switch (n.shape) {
      case 'ellipse':
        svg += `<ellipse cx="${nx + n.w / 2}" cy="${ny + n.h / 2}" rx="${n.w / 2}" ry="${n.h / 2}" fill="${n.fillColor}" stroke="${n.strokeColor}" stroke-width="1.5"/>`
        break
      case 'rhombus':
        const cx = nx + n.w / 2, cy = ny + n.h / 2
        svg += `<polygon points="${cx},${cy - n.h / 2} ${cx + n.w / 2},${cy} ${cx},${cy + n.h / 2} ${cx - n.w / 2},${cy}" fill="${n.fillColor}" stroke="${n.strokeColor}" stroke-width="1.5"/>`
        break
      case 'hexagon': {
        const cx = nx + n.w / 2
        const cy = ny + n.h / 2
        const hw = n.w / 2
        const hh = n.h / 2
        const inset = Math.min(n.w, n.h) * 0.2
        svg += `<polygon points="${cx + hw - inset},${cy - hh} ${cx + hw},${cy} ${cx + hw - inset},${cy + hh} ${cx - hw + inset},${cy + hh} ${cx - hw},${cy} ${cx - hw + inset},${cy - hh}" fill="${n.fillColor}" stroke="${n.strokeColor}" stroke-width="1.5"/>`
        break
      }
      default: {
        const r = n.rounded ? 8 : 4
        svg += `<rect x="${nx}" y="${ny}" width="${n.w}" height="${n.h}" rx="${r}" ry="${r}" fill="${n.fillColor}" stroke="${n.strokeColor}" stroke-width="1.5"/>`
        break
      }
    }

    // Label
    if (n.label) {
      const cx = nx + n.w / 2
      const cy = ny + n.h / 2
      svg += `<text x="${cx}" y="${cy}" text-anchor="middle" dominant-baseline="middle" font-size="${n.fontSize}" font-family="${n.fontFamily}" fill="#333">${escapeHtml(n.label)}</text>`
    }
  }

  svg += '</svg>'
  return svg
}

function escapeHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}

/* ===== 渲染逻辑 ===== */

let svgOverlay: HTMLDivElement | null = null

function renderDiagram(xml: string) {
  nextTick(() => {
    if (!svgOverlay) {
      svgOverlay = document.createElement('div')
      svgOverlay.className = 'diagram-svg-wrapper'
      const container = svgContainerRef.value
      if (container) {
        container.appendChild(svgOverlay)
      }
    }

    const { nodes, edges } = parseDrawioXml(xml)
    const svgStr = generateSvg(nodes, edges)
    if (svgOverlay) {
      svgOverlay.innerHTML = svgStr
    }
  })
}

watch(() => store.currentXml, (xml) => {
  if (xml) {
    // Clear placeholder
    const placeholder = svgContainerRef.value?.querySelector('.placeholder')
    if (placeholder) placeholder.style.display = 'none'
    renderDiagram(xml)
  }
})

onMounted(() => {
  store.initSession()
})

onBeforeUnmount(() => {
  if (svgOverlay && svgOverlay.parentNode) {
    svgOverlay.parentNode.removeChild(svgOverlay)
  }
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  width: 100%;
  height: 100%;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  color: #1a1a1a;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

/* ===== Toolbar ===== */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
  z-index: 100;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-name {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-toolbar {
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 10px;
  padding: 8px;
  cursor: pointer;
  color: #667eea;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  align-items: center;
}
.btn-toolbar:hover {
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}
.btn-toolbar.active {
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-color: transparent;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

/* ===== Canvas area ===== */
.canvas-area {
  flex: 1;
  overflow: hidden;
  position: relative;
  min-height: 0;
  padding: 20px;
  gap: 20px;
}

.canvas-area.side {
  display: flex;
  flex-direction: row;
}

.canvas-area.bottom {
  display: flex;
  flex-direction: column;
}

.drawio-canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  position: relative;
  overflow: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.canvas-area.side .drawio-canvas {
  height: 100%;
}

.canvas-area.bottom .drawio-canvas {
  flex: 1;
  min-height: 0;
}

/* ===== Placeholder ===== */
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #888;
  gap: 16px;
}
.placeholder p {
  font-size: 15px;
  font-weight: 500;
}
.placeholder svg {
  filter: drop-shadow(0 2px 8px rgba(102, 126, 234, 0.2));
}

/* ===== Diagram SVG ===== */
:deep(.diagram-svg-wrapper) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}

:deep(.diagram-svg-wrapper svg) {
  max-width: 100%;
  max-height: 100%;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  border-radius: 16px;
  background: #fff;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
:deep(.diagram-svg-wrapper svg:hover) {
  transform: scale(1.01);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
}

/* ===== Chat overlays ===== */
.chat-overlay-side {
  width: 420px;
  height: 100%;
  flex-shrink: 0;
}

.chat-overlay-bottom {
  height: 400px;
  width: 100%;
  flex-shrink: 0;
}
</style>
