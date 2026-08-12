import { defineStore } from 'pinia';
import { ref } from 'vue';
import axios from 'axios';
import type { SeniorSkill } from '../types/index';

export const useSeniorStore = defineStore('senior', () => {
  const items = ref<SeniorSkill[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const selectedDomain = ref<string>('');
  const selectedSchool = ref<string>('');

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const res = await axios.get('/api/seniors');
      items.value = (res.data?.items ?? []) as SeniorSkill[];
    } catch (e) {
      error.value = (e as Error).message;
      items.value = [];
    } finally {
      loading.value = false;
    }
  }

  return { items, loading, error, selectedDomain, selectedSchool, load };
});
