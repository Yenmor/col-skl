import { defineStore } from 'pinia'
import { ref } from 'vue'
import { skillDomains, type DomainId } from '../domain'
import { requestAbilityProfile, type AbilityProfile } from '../services/abilityProfile'

export const useAbilitySpaceStore = defineStore('ability-space', () => {
  const activeId = ref<DomainId>('skills')
  const expandedId = ref<DomainId | null>(null)
  const homeMode = ref<'chat' | 'cube'>('chat')
  const communityPhase = ref<'idle' | 'entering' | 'open' | 'leaving'>('idle')
  // Read-only bridge for the existing cube-to-profile morph. The cube owns
  // the animation; profile UI receives only two threshold changes.
  const profileRevealPhase = ref<'hidden' | 'outline' | 'ready'>('hidden')
  const cubeCommand = ref<{ id: number; type: 'left' | 'right' | 'reset' | 'motion' }>({ id: 0, type: 'reset' })

  // 画像数据预加载缓存：App 启动即取，MeView 挂载时直接消费，
  // 立方体转圈收缩（morph）读到的 skillDomains.score 也来自这里。
  const abilityProfile = ref<AbilityProfile | null>(null)
  const profilePreloadState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')

  function syncProfileScores(profile: AbilityProfile) {
    for (const local of skillDomains) {
      const found = profile.domains.find(domain => domain.id === local.id
        || domain.name === local.name
        || local.aliases.some(alias => alias && domain.name.includes(alias)))
      if (found) local.score = found.score
    }
  }

  async function preloadProfile() {
    if (profilePreloadState.value === 'ready' || profilePreloadState.value === 'loading') return
    profilePreloadState.value = 'loading'
    try {
      const profile = await requestAbilityProfile()
      abilityProfile.value = profile
      syncProfileScores(profile)
      profilePreloadState.value = 'ready'
    } catch {
      profilePreloadState.value = 'error'
    }
  }

  function select(id: DomainId) {
    activeId.value = id
  }

  function showCube() {
    homeMode.value = 'cube'
  }

  function showChat() {
    homeMode.value = 'chat'
  }

  function prepareProfile() {
    homeMode.value = 'cube'
    communityPhase.value = 'idle'
    expandedId.value = null
    profileRevealPhase.value = 'hidden'
  }

  function setProfileRevealPhase(phase: 'hidden' | 'outline' | 'ready') {
    profileRevealPhase.value = phase
  }

  function beginCommunity(id: DomainId) {
    activeId.value = id
    homeMode.value = 'cube'
    expandedId.value = null
    communityPhase.value = 'entering'
  }

  function openCommunity(id?: DomainId) {
    if (id) activeId.value = id
    communityPhase.value = 'open'
  }

  function leaveCommunity() {
    communityPhase.value = 'leaving'
  }

  function finishCommunityExit() {
    communityPhase.value = 'idle'
    homeMode.value = 'cube'
  }

  function commandCube(type: 'left' | 'right' | 'reset' | 'motion') {
    cubeCommand.value = { id: cubeCommand.value.id + 1, type }
  }

  function toggleExpanded(id: DomainId) {
    expandedId.value = expandedId.value === id ? null : id
    activeId.value = id
  }

  function closeExpanded() {
    expandedId.value = null
  }

  return {
    activeId,
    expandedId,
    homeMode,
    communityPhase,
    profileRevealPhase,
    cubeCommand,
    abilityProfile,
    profilePreloadState,
    preloadProfile,
    select,
    showCube,
    showChat,
    prepareProfile,
    setProfileRevealPhase,
    beginCommunity,
    openCommunity,
    leaveCommunity,
    finishCommunityExit,
    commandCube,
    toggleExpanded,
    closeExpanded,
  }
})
