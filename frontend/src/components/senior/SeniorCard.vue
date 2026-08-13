<template>
  <article
    class="skill-directory-row"
    :style="domainStyle"
  >
    <span class="skill-directory-mark" aria-hidden="true">{{ domain.glyph }}</span>

    <div class="skill-directory-main">
      <div class="skill-directory-labels">
        <span class="domain-tag">{{ senior.domain || domain.name }}</span>
        <span v-if="senior.visibility === 'PRIVATE'" class="visibility-tag"><LockKeyhole :size="11" />私有草稿</span>
      </div>
      <h2><RouterLink :to="`/seniors/${senior.id}`">{{ senior.name }}</RouterLink></h2>
      <p>{{ senior.summary || '后端尚未提供这份 Skill 的摘要。' }}</p>
      <div v-if="senior.tags?.length" class="skill-directory-tags">
        <span v-for="tag in senior.tags" :key="tag">#{{ tag }}</span>
      </div>
      <dl class="skill-directory-meta">
        <div><dt>版本</dt><dd>{{ senior.version || '未标注' }}</dd></div>
        <div><dt>贡献者</dt><dd>{{ contributor }}</dd></div>
        <div><dt>更新</dt><dd>{{ formattedDate }}</dd></div>
      </dl>
    </div>

    <div class="skill-directory-fit">
      <template v-if="trust">
        <SkillFitPolygon :scores="trust.scores" :overall="trust.overall" :level="trust.level" compact />
        <div>
          <strong>{{ trust.level }}</strong>
          <span>{{ trust.overall }} / 100 · Skill 包质量</span>
          <p>只评估七件套、方法与边界完整度，不评判经历真伪。</p>
          <small>来源确认、平台核验和社区采用请在详情核对。</small>
        </div>
      </template>
      <div v-else class="trust-unavailable">
        <CircleHelp :size="18" />
        <span><strong>包质量指标不可用</strong><small>离线索引不推算七件套质量。</small></span>
      </div>
    </div>

    <div v-if="match" class="skill-match-result">
      <span>{{ recallMatchLabel(match) }}</span>
      <strong>{{ Math.round(match.score * 100) }}<small>%</small></strong>
      <p>{{ match.text || '召回服务认为这份 Skill 与当前任务相关。' }}</p>
    </div>

    <div class="skill-directory-actions">
      <button type="button" title="下载完整 Skill 包" aria-label="下载完整 Skill 包" @click.stop="$emit('download', senior)"><Download :size="16" /></button>
      <button type="button" class="use-skill" @click.stop="$emit('use', senior)"><MessageCircleQuestion :size="15" />调用</button>
      <button type="button" title="查看详情" aria-label="查看 Skill 详情" @click.stop="$emit('open', senior.id)"><ArrowUpRight :size="17" /></button>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowUpRight, CircleHelp, Download, LockKeyhole, MessageCircleQuestion } from '@lucide/vue'
import { domainForLabel, skillDomains } from '../../domain'
import { recallMatchLabel, skillTrustFor } from '../../skillFit'
import type { SeniorSkill, SkillRecallMatch } from '../../skillsProfileTypes'
import SkillFitPolygon from '../skillslab/SkillFitPolygon.vue'

const props = defineProps<{ senior: SeniorSkill; match?: SkillRecallMatch }>()
defineEmits<{ open: [id: string]; use: [skill: SeniorSkill]; download: [skill: SeniorSkill] }>()

const domain = computed(() => skillDomains.find(item => item.id === props.senior.layerId)
  ?? domainForLabel(`${props.senior.domain} ${props.senior.name}`))
const domainStyle = computed(() => ({
  '--domain-color': domain.value.color,
  '--domain-ink': domain.value.ink,
  '--domain-tint': domain.value.tint,
}))
const trust = computed(() => skillTrustFor(props.senior))
const contributor = computed(() => [props.senior.school, props.senior.major].filter(Boolean).join(' · ') || '未提供')
const formattedDate = computed(() => {
  const value = props.senior.updatedAt || props.senior.createdAt
  if (!value) return '未提供'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
})
</script>
