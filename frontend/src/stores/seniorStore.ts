import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { SeniorSkill } from '../types'
import { fetchSeniors } from '../services/seniorService'

export const useSeniorStore = defineStore('senior', () => {
  const items = ref<SeniorSkill[]>([])
  const loading = ref(false)
  const selectedDomain = ref('全部')
  const selectedSchool = ref('全部')

  const schools = computed(() => [...new Set(items.value.map(item => item.school))])

  async function load() {
    loading.value = true
    items.value = await fetchSeniors(
      selectedDomain.value === '全部' ? undefined : selectedDomain.value,
      selectedSchool.value === '全部' ? undefined : selectedSchool.value,
    )
    loading.value = false
  }

  return { items, loading, selectedDomain, selectedSchool, schools, load }
})
