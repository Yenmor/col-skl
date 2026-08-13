<template>
  <header class="topbar">
    <div class="content-width topbar-inner">
      <div class="topbar-title">
        <span class="topbar-mark" :style="{ background: accent }" />
        <span><strong>{{ section }}</strong><small>{{ title }}</small></span>
      </div>
      <div class="topbar-actions">
        <button v-if="actionLabel" class="btn-text" type="button" @click="$emit('action')"><Plus :size="16" />{{ actionLabel }}</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Plus } from '@lucide/vue'

defineEmits<{ (e: 'action'): void }>()
defineProps<{ actionLabel?: string }>()

const route = useRoute()
const isProfile = computed(() => route.path.startsWith('/me'))
const isStudio = computed(() => route.path.startsWith('/seniors/studio'))
const isDetail = computed(() => route.path.startsWith('/seniors/'))
const section = computed(() => isProfile.value ? '个人能力' : isStudio.value ? 'SkillsLab · 沉淀台' : 'SkillsLab · Skill 仓库')
const title = computed(() => isProfile.value ? '能力证据与我的 Skills' : isStudio.value ? '从本人经验到私有 Skill 草稿' : isDetail.value ? '可调用的经验与方法' : '校园经验沉淀的方法库')
const accent = computed(() => isProfile.value ? '#ff835f' : isStudio.value ? '#b98cff' : '#47cfff')
</script>
