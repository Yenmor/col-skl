<template>
  <figure class="skill-fit-polygon" :class="{ compact }" :aria-label="ariaLabel">
    <svg viewBox="0 0 180 180" role="img">
      <title>{{ ariaLabel }}</title>
      <polygon v-for="scale in [.25,.5,.75,1]" :key="scale" :points="points(scores.map(() => scale))" class="fit-grid" />
      <line v-for="point in outerPoints" :key="`${point.x}-${point.y}`" x1="90" y1="90" :x2="point.x" :y2="point.y" />
      <polygon :points="points(scores.map(score => score / 100))" class="fit-score-shape" />
      <circle v-for="point in scorePoints" :key="`${point.x}-${point.y}`" :cx="point.x" :cy="point.y" r="2.5" />
      <template v-if="!compact">
        <text v-for="(point, index) in labelPoints" :key="labels[index]" :x="point.x" :y="point.y" :text-anchor="point.anchor" dominant-baseline="middle">{{ labels[index] }}</text>
      </template>
    </svg>
    <figcaption><strong>{{ overall }}</strong><span>{{ level }}</span></figcaption>
  </figure>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { fitAxisLabels, type FitScores } from '../../skillFit'

const props = defineProps<{ scores: FitScores; overall: number; level: string; compact?: boolean }>()
const labels = fitAxisLabels
const coordinates = (values: number[], radius = 58) => values.map((value, index) => {
  const angle = -Math.PI / 2 + index * Math.PI * 2 / values.length
  return { x: 90 + Math.cos(angle) * radius * value, y: 90 + Math.sin(angle) * radius * value }
})
const points = (values: number[]) => coordinates(values).map(point => `${point.x},${point.y}`).join(' ')
const outerPoints = computed(() => coordinates(props.scores.map(() => 1)))
const scorePoints = computed(() => coordinates(props.scores.map(score => score / 100)))
const labelPoints = computed(() => coordinates(props.scores.map(() => 1), 78).map((point, index) => ({ ...point, anchor: index === 0 ? 'middle' : point.x > 92 ? 'start' : point.x < 88 ? 'end' : 'middle' } as const)))
const ariaLabel = computed(() => `${props.level}，Skill 信任度 ${props.overall}。${labels.map((label, index) => `${label} ${props.scores[index]}`).join('，')}`)
</script>

<style scoped>
.skill-fit-polygon { position: relative; width: min(100%, 330px); margin: 0; color: var(--domain-ink, var(--focus)); }
.skill-fit-polygon svg { width: 100%; height: auto; display: block; overflow: visible; }
.fit-grid { fill: none; stroke: var(--line); stroke-width: .75; vector-effect: non-scaling-stroke; }
line { stroke: var(--line); stroke-width: .65; vector-effect: non-scaling-stroke; }
.fit-score-shape { fill: color-mix(in srgb, var(--domain-color, var(--skills)) 27%, transparent); stroke: var(--domain-ink, var(--focus)); stroke-width: 1.8; stroke-linejoin: round; vector-effect: non-scaling-stroke; transition: points 380ms var(--ease-out); }
circle { fill: var(--domain-color, var(--skills)); stroke: var(--domain-ink, var(--focus)); stroke-width: 1; vector-effect: non-scaling-stroke; }
text { fill: var(--ink-soft); font: 700 8px var(--font-sans); }
figcaption { position: absolute; inset: 50% auto auto 50%; display: grid; place-items: center; min-width: 46px; padding: 4px 6px; background: color-mix(in srgb, var(--surface) 88%, transparent); transform: translate(-50%, -50%); pointer-events: none; }
figcaption strong { color: var(--ink); font-size: 16px; line-height: 1; }
figcaption span { margin-top: 2px; color: var(--domain-ink, var(--focus)); font-size: 6px; font-weight: 850; }
.compact { width: 100px; flex: 0 0 100px; }
.compact figcaption { min-width: 32px; padding: 3px; }
.compact figcaption strong { font-size: 11px; }
.compact figcaption span { display: none; }
@media (prefers-reduced-motion: reduce) { .fit-score-shape { transition: none; } }
</style>
