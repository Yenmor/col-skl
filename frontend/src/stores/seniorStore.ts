import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { domainForLabel, skillDomains } from '../domain'
import { fetchSeniors, recallSkills } from '../services/seniorService'
import type { SeniorSkill, SkillCatalogSource, SkillRecallMatch } from '../skillsProfileTypes'

export const useSeniorStore = defineStore('senior', () => {
  const items = ref<SeniorSkill[]>([])
  const loading = ref(false)
  const error = ref('')
  const catalogSource = ref<SkillCatalogSource>('api')
  const facetSchools = ref<string[]>([])
  const selectedDomain = ref('全部')
  const selectedSchool = ref('全部')
  const query = ref('')
  const selectedTag = ref('全部')
  const sort = ref<'updated' | 'name'>('updated')
  const taskContext = ref('')
  const matches = ref<Map<string, SkillRecallMatch>>(new Map())
  const matching = ref(false)
  const matchError = ref('')
  const matchedTask = ref('')

  const backendUnavailable = computed(() => catalogSource.value !== 'api')
  const schools = computed(() => [...new Set([...facetSchools.value, ...items.value.map(item => item.school).filter(Boolean)])])
  const tags = computed(() => [...new Set(items.value.flatMap(item => item.tags ?? []))])
  const hasTaskMatches = computed(() => Boolean(matchedTask.value) && matches.value.size > 0)

  const visibleItems = computed(() => items.value.filter(item => {
    const selectedLayer = skillDomains.find(domain => domain.name === selectedDomain.value)
    const domainMatch = selectedDomain.value === '全部'
      || (selectedLayer && item.layerId === selectedLayer.id)
      || item.domain === selectedDomain.value
      || domainForLabel(`${item.domain} ${item.name}`).name === selectedDomain.value
    const schoolMatch = selectedSchool.value === '全部' || item.school === selectedSchool.value
    const tagMatch = selectedTag.value === '全部' || item.tags?.includes(selectedTag.value)
    const terms = `${item.name} ${item.summary ?? ''} ${item.domain} ${item.school} ${item.major} ${(item.tags ?? []).join(' ')}`.toLowerCase()
    const queryMatch = terms.includes(query.value.trim().toLowerCase())
    return domainMatch && schoolMatch && tagMatch && queryMatch
  }).sort((a, b) => {
    if (hasTaskMatches.value) {
      const scoreA = matches.value.get(a.id)?.score ?? -1
      const scoreB = matches.value.get(b.id)?.score ?? -1
      if (scoreA !== scoreB) return scoreB - scoreA
    }
    if (sort.value === 'name') return a.name.localeCompare(b.name, 'zh-CN')
    return String(b.updatedAt ?? b.createdAt ?? '').localeCompare(String(a.updatedAt ?? a.createdAt ?? ''))
  }))

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const result = await fetchSeniors()
      items.value = result.items
      catalogSource.value = result.source
      facetSchools.value = result.facets.schools
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : 'Skill 仓库加载失败'
      items.value = []
    } finally {
      loading.value = false
    }
  }

  async function matchTask() {
    const task = taskContext.value.trim()
    if (!task) {
      matches.value = new Map()
      matchedTask.value = ''
      matchError.value = ''
      return
    }
    matching.value = true
    matchError.value = ''
    try {
      matches.value = await recallSkills(task, 20)
      matchedTask.value = task
    } catch (cause) {
      matches.value = new Map()
      matchedTask.value = ''
      matchError.value = cause instanceof Error ? cause.message : '任务匹配暂时不可用'
    } finally {
      matching.value = false
    }
  }

  function clearTaskMatch() {
    taskContext.value = ''
    matches.value = new Map()
    matchedTask.value = ''
    matchError.value = ''
  }

  return {
    items,
    visibleItems,
    loading,
    error,
    catalogSource,
    backendUnavailable,
    selectedDomain,
    selectedSchool,
    selectedTag,
    query,
    sort,
    taskContext,
    matches,
    matching,
    matchError,
    matchedTask,
    hasTaskMatches,
    schools,
    tags,
    load,
    matchTask,
    clearTaskMatch,
  }
})
