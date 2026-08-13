<template>
  <Teleport to="body">
    <Transition name="trace-drawer">
      <div v-if="open" class="trace-overlay" @click.self="close">
        <aside ref="panel" class="trace-drawer" role="dialog" aria-modal="true" :aria-labelledby="titleId" @keydown.esc="close">
          <header><div><small>{{ kind }} · 来源追踪</small><h2 :id="titleId" tabindex="-1">{{ title }}</h2></div><button type="button" title="关闭来源追踪" aria-label="关闭来源追踪" @click="close"><X :size="19" /></button></header>
          <div class="trace-summary"><BadgeCheck :size="17" /><span><strong>2 个支持 · 1 个限制 · 1 个背景</strong><small>没有把点赞数换算成统一可信度</small></span></div>
          <nav aria-label="选择来源"><button v-for="(source, index) in sources" :key="source.title" type="button" :class="{ active: selected === index }" @click="selected = index"><span :class="`relation-${source.relation}`">{{ relationLabel(source.relation) }}</span><strong>{{ source.title }}</strong><small>{{ source.author }}</small></button></nav>
          <article><header><span :class="`relation-${current.relation}`">{{ relationLabel(current.relation) }}</span><em>{{ current.status }}</em></header><blockquote>{{ current.quote }}</blockquote><p>{{ current.context }}</p><dl><div><dt>适用条件</dt><dd>{{ current.condition }}</dd></div><div><dt>版本与授权</dt><dd>{{ current.version }}</dd></div></dl></article>
          <footer><RouterLink :to="current.href">打开原讨论 <ArrowUpRight :size="14" /></RouterLink><button type="button">报告过时或断章取义</button></footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowUpRight, BadgeCheck, X } from '@lucide/vue'

type TraceSource = { relation: 'support' | 'limit' | 'context'; title: string; author: string; status: string; quote: string; context: string; condition: string; version: string; href: string }
const props = defineProps<{ open: boolean; title: string; kind?: string; sources?: TraceSource[] }>()
const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement | null>(null)
const selected = ref(0)
const titleId = `trace-title-${Math.random().toString(36).slice(2)}`
const fallbackSources: TraceSource[] = [
  { relation: 'support', title: '第一次做小型实验的复盘', author: '周予安 · 科研层', status: '当前', quote: '先写评价指标，再决定实验怎样跑，否则很容易在结果出来后改标准。', context: '来自完整复盘的第二段，后续小型对照实验按这份检查完成。', condition: '适用于可控制变量的小型课程实验', version: '2026-08-09 · 已授权派生', href: '/community?domain=research' },
  { relation: 'support', title: '课程论文要求核对', author: '课程公开材料', status: '当前', quote: '最终提交必须包括研究问题、相关工作、方法、结果与引用。', context: '用于确认最终产物结构，不证明具体方法一定有效。', condition: '仅适用于当前课程要求', version: '2026 秋季版 · 事实来源', href: '/community?domain=study' },
  { relation: 'limit', title: '已有导师命题时的路径', author: '唐婧 · 科研层', status: '当前', quote: '如果题目和数据已经给定，先缩小选题并不是第一步，应先确认评价口径。', context: '这条经历限制了当前建议的适用范围。', condition: '已有固定命题、数据和导师要求', version: '2026-08-02 · 已授权引用', href: '/community?domain=research' },
  { relation: 'context', title: '同阶段学生的常见卡点', author: '社区讨论汇总', status: '待复核', quote: '多数回复提到范围过大，但这些回复没有后续结果记录。', context: '帮助理解情境，不计入直接依据。', condition: '只作为背景，不作为结论证明', version: '18 条公开讨论 · 候选摘要', href: '/community?domain=research' },
]
const sources = computed(() => props.sources?.length ? props.sources : fallbackSources)
const current = computed(() => sources.value[selected.value] ?? sources.value[0])

function relationLabel(relation: TraceSource['relation']) { return relation === 'support' ? '支持' : relation === 'limit' ? '限制' : '背景' }
function close() { emit('close') }
watch(() => props.open, value => { if (value) nextTick(() => panel.value?.querySelector<HTMLElement>('h2')?.focus()) })
</script>
