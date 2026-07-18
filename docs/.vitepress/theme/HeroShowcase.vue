<script setup lang="ts">
import type { Edge, Node, NodeDragEvent } from '@vue-flow/core'
import { Handle, Position, VueFlow } from '@vue-flow/core'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { heroBranches } from './home-content'

interface HeroNodeData {
  title: string
  caption: string
  handle?: Position
}

const initialPositions = [
  { x: 28, y: 58, handle: Position.Right, sourceHandle: 'left' },
  { x: 286, y: 14, handle: Position.Bottom, sourceHandle: 'top' },
  { x: 724, y: 14, handle: Position.Bottom, sourceHandle: 'top' },
  { x: 950, y: 58, handle: Position.Left, sourceHandle: 'right' },
  { x: 950, y: 354, handle: Position.Left, sourceHandle: 'right' },
  { x: 724, y: 416, handle: Position.Top, sourceHandle: 'bottom' },
  { x: 286, y: 416, handle: Position.Top, sourceHandle: 'bottom' },
  { x: 28, y: 354, handle: Position.Right, sourceHandle: 'left' }
]

function createNodes(): Node<HeroNodeData>[] {
  return [
    {
      id: 'platform-core',
      type: 'core',
      position: { x: 515, y: 188 },
      draggable: true,
      selectable: false,
      data: { title: 'Sca4d-J', caption: 'Java 应用平台' }
    },
    ...heroBranches.map((branch, index) => ({
      id: branch.id,
      type: 'branch',
      position: { x: initialPositions[index].x, y: initialPositions[index].y },
      data: {
        title: branch.title,
        caption: branch.caption,
        handle: initialPositions[index].handle
      }
    }))
  ]
}

const nodes = ref<Node<HeroNodeData>[]>(createNodes())
const activeId = ref(heroBranches[0].id)
const desktop = ref(true)
let desktopQuery: MediaQueryList | undefined

const edges: Edge[] = heroBranches.map((branch, index) => ({
  id: `platform-${branch.id}`,
  source: 'platform-core',
  sourceHandle: initialPositions[index].sourceHandle,
  target: branch.id,
  type: 'default',
  animated: false,
  class: 'is-flowing'
}))

const activeBranch = computed(() => heroBranches.find((branch) => branch.id === activeId.value) ?? heroBranches[0])

function activate(id: string): void {
  activeId.value = id
}

function onDragStart(event: NodeDragEvent): void {
  if (event.node.id !== 'platform-core') activate(event.node.id)
}

function updateDesktop(event?: MediaQueryListEvent): void {
  desktop.value = event ? event.matches : (desktopQuery?.matches ?? true)
}

onMounted(() => {
  desktopQuery = window.matchMedia('(min-width: 1180px)')
  updateDesktop()
  desktopQuery.addEventListener('change', updateDesktop)
})

onBeforeUnmount(() => {
  desktopQuery?.removeEventListener('change', updateDesktop)
})
</script>

<template>
  <div class="hero-platform-scene" aria-label="Sca4d-J 工程组成">
    <div class="hero-platform-scene__browser-bar" aria-hidden="true">
      <div class="hero-platform-scene__window-actions">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div class="hero-platform-scene__address">jdev.ncraft.net</div>
      <span class="hero-platform-scene__browser-spacer"></span>
    </div>

    <div class="hero-platform-scene__canvas">
      <VueFlow
        v-model:nodes="nodes"
        :edges="edges"
        :nodes-draggable="desktop"
        :node-extent="[[0, 0], [1160, 530]]"
        :nodes-connectable="false"
        :edges-updatable="false"
        :elements-selectable="false"
        :auto-pan-on-node-drag="false"
        :pan-on-drag="false"
        :zoom-on-scroll="false"
        :zoom-on-pinch="false"
        :zoom-on-double-click="false"
        :prevent-scrolling="false"
        :default-viewport="{ x: 0, y: 0, zoom: 1 }"
        :min-zoom="1"
        :max-zoom="1"
        @node-drag-start="onDragStart"
      >
        <template #node-core="{ data }">
          <div class="hero-platform-scene__core" aria-live="polite">
            <span class="hero-platform-scene__core-ring" aria-hidden="true"></span>
            <img src="/logo.png" alt="" width="52" height="52">
            <strong>{{ data.title }}</strong>
            <span>{{ activeBranch.title }}</span>
            <Handle id="top" type="source" :position="Position.Top" />
            <Handle id="right" type="source" :position="Position.Right" />
            <Handle id="bottom" type="source" :position="Position.Bottom" />
            <Handle id="left" type="source" :position="Position.Left" />
          </div>
        </template>

        <template #node-branch="{ id, data }">
          <button
            type="button"
            class="hero-platform-scene__node"
            :class="{ 'is-active': activeId === id }"
            :aria-pressed="activeId === id"
            @mouseenter="activate(id)"
            @focus="activate(id)"
            @click="activate(id)"
          >
            <Handle type="target" :position="data.handle ?? Position.Left" />
            <span class="hero-platform-scene__node-led" aria-hidden="true"></span>
            <strong>{{ data.title }}</strong>
            <small>{{ data.caption }}</small>
          </button>
        </template>
      </VueFlow>
    </div>
  </div>
</template>

<style scoped>
.hero-platform-scene {
  position: relative;
  width: min(1240px, calc(100% - 32px));
  margin: 0 auto;
  overflow: hidden;
  background: var(--home-surface);
  border: 1px solid var(--home-border);
  border-radius: 8px;
  box-shadow: var(--home-shadow-lg);
  isolation: isolate;
}

.hero-platform-scene__browser-bar {
  display: grid;
  grid-template-columns: 72px minmax(120px, 420px) 72px;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 16px;
  background: var(--home-surface-muted);
  border-bottom: 1px solid var(--home-border);
}

.hero-platform-scene__window-actions {
  display: flex;
  gap: 7px;
}

.hero-platform-scene__window-actions span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.hero-platform-scene__window-actions span:nth-child(1) { background: #ed6a5e; }
.hero-platform-scene__window-actions span:nth-child(2) { background: #f4bf4f; }
.hero-platform-scene__window-actions span:nth-child(3) { background: #61c554; }

.hero-platform-scene__address {
  overflow: hidden;
  padding: 6px 14px;
  color: var(--home-text-3);
  font-size: 12px;
  line-height: 1.2;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: var(--home-surface);
  border: 1px solid var(--home-border);
  border-radius: 5px;
}

.hero-platform-scene__browser-spacer {
  width: 72px;
}

.hero-platform-scene__canvas {
  position: relative;
  height: 520px;
  overflow: hidden;
}

.hero-platform-scene__canvas::before {
  position: absolute;
  inset: 0;
  content: "";
  background-image:
    linear-gradient(var(--home-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--home-grid) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: linear-gradient(to bottom, transparent, #000 15%, #000 86%, transparent);
}

.hero-platform-scene :deep(.vue-flow),
.hero-platform-scene :deep(.vue-flow__viewport) {
  overflow: hidden;
}

.hero-platform-scene :deep(.vue-flow__pane) {
  cursor: default;
}

.hero-platform-scene :deep(.vue-flow__node) {
  cursor: grab;
}

.hero-platform-scene :deep(.vue-flow__node.dragging) {
  cursor: grabbing;
  z-index: 8 !important;
}

.hero-platform-scene :deep(.vue-flow__node-core) {
  cursor: grab;
}

.hero-platform-scene :deep(.vue-flow__edge-path) {
  stroke: var(--home-line);
  stroke-width: 1.25;
  transition: stroke 180ms ease, stroke-width 180ms ease, opacity 180ms ease;
}

.hero-platform-scene :deep(.vue-flow__edge.is-flowing .vue-flow__edge-path) {
  stroke-dasharray: 5 14;
  animation: hero-edge-flow 1.6s linear infinite;
  will-change: stroke-dashoffset;
}

.hero-platform-scene :deep(.vue-flow__handle) {
  width: 2px;
  height: 2px;
  min-width: 2px;
  min-height: 2px;
  opacity: 0;
  pointer-events: none;
}

.hero-platform-scene__core {
  position: relative;
  display: grid;
  place-items: center;
  width: 170px;
  height: 170px;
  padding: 24px;
  color: var(--home-brand-strong);
  text-align: center;
  background: var(--home-surface-glass);
  border: 1px solid var(--home-border-strong);
  border-radius: 50%;
  box-shadow: var(--home-shadow-lg);
}

.hero-platform-scene__core-ring {
  position: absolute;
  inset: -13px;
  z-index: -1;
  border: 1px solid var(--home-brand-border);
  border-radius: 50%;
  animation: hero-core-ring 3.8s ease-in-out infinite;
}

.hero-platform-scene__core img {
  width: 52px;
  height: 52px;
  object-fit: contain;
}

.hero-platform-scene__core strong {
  margin-top: 7px;
  font-size: 19px;
  line-height: 1.1;
}

.hero-platform-scene__core > span:nth-child(4) {
  min-height: 18px;
  margin-top: 4px;
  color: var(--home-text-2);
  font-size: 12px;
  font-weight: 650;
}

.hero-platform-scene__node {
  position: relative;
  width: 190px;
  min-height: 74px;
  padding: 13px 14px 12px 28px;
  color: var(--home-text-1);
  text-align: left;
  background: var(--home-surface-glass);
  border: 1px solid var(--home-border);
  border-radius: 8px;
  box-shadow: var(--home-shadow-sm);
  cursor: grab;
  transition: color 180ms ease, background-color 180ms ease, border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.hero-platform-scene__node:hover,
.hero-platform-scene__node:focus-visible,
.hero-platform-scene__node.is-active {
  color: var(--home-brand-strong);
  background: var(--home-surface);
  border-color: var(--home-brand-border-strong);
  box-shadow: var(--home-shadow-md);
}

.hero-platform-scene__node-led {
  position: absolute;
  top: 20px;
  left: 13px;
  width: 7px;
  height: 7px;
  background: var(--home-text-3);
  border-radius: 50%;
}

.hero-platform-scene__node.is-active .hero-platform-scene__node-led {
  background: var(--home-brand);
  box-shadow: 0 0 0 6px var(--home-brand-soft);
  animation: hero-led 1.4s ease-in-out infinite;
}

.hero-platform-scene__node strong {
  display: block;
  font-size: 14px;
  line-height: 1.3;
}

.hero-platform-scene__node small {
  display: block;
  margin-top: 5px;
  color: var(--home-text-3);
  font-size: 11px;
  line-height: 1.35;
}

@keyframes hero-core-ring {
  50% { opacity: 0.45; transform: scale(1.055); }
}

@keyframes hero-edge-flow {
  to { stroke-dashoffset: -76; }
}

@keyframes hero-led {
  50% { box-shadow: 0 0 0 10px transparent; }
}

@media (max-width: 1179px) {
  .hero-platform-scene__canvas {
    height: auto;
    padding-top: 154px;
  }

  .hero-platform-scene__canvas::before,
  .hero-platform-scene :deep(.vue-flow__edges) {
    display: none;
  }

  .hero-platform-scene :deep(.vue-flow) {
    position: static;
    height: auto;
  }

  .hero-platform-scene :deep(.vue-flow__viewport) {
    position: static;
  }

  .hero-platform-scene :deep(.vue-flow__transformationpane) {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    transform: none !important;
    pointer-events: auto;
  }

  .hero-platform-scene :deep(.vue-flow__node) {
    position: relative;
    width: auto !important;
    transform: none !important;
    pointer-events: auto;
  }

  .hero-platform-scene :deep(.vue-flow__node-core) {
    position: absolute;
    top: -154px;
    left: 50%;
    transform: translateX(-50%) !important;
  }

  .hero-platform-scene__core {
    width: 136px;
    height: 136px;
    padding: 18px;
  }

  .hero-platform-scene__core img { width: 40px; height: 40px; }
  .hero-platform-scene__node { width: 100%; min-height: 72px; cursor: pointer; }
}

@media (max-width: 560px) {
  .hero-platform-scene { width: calc(100% - 24px); }
  .hero-platform-scene__browser-bar { grid-template-columns: 52px minmax(0, 1fr) 52px; gap: 8px; padding-inline: 10px; }
  .hero-platform-scene__browser-spacer { width: 52px; }
  .hero-platform-scene__window-actions { gap: 5px; }
  .hero-platform-scene__window-actions span { width: 8px; height: 8px; }
  .hero-platform-scene :deep(.vue-flow__transformationpane) { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .hero-platform-scene :deep(.vue-flow__edge.is-flowing .vue-flow__edge-path),
  .hero-platform-scene__core-ring,
  .hero-platform-scene__node-led {
    animation: none !important;
  }
}
</style>
