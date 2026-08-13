import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DomainId } from '../domain'

export const useAbilitySpaceStore = defineStore('ability-space', () => {
  const activeId = ref<DomainId>('skills')
  const expandedId = ref<DomainId | null>(null)
  const homeMode = ref<'chat' | 'cube'>('chat')
  const communityPhase = ref<'idle' | 'entering' | 'open' | 'leaving'>('idle')
  // Read-only bridge for the existing cube-to-profile morph. The cube owns
  // the animation; profile UI receives only two threshold changes.
  const profileRevealPhase = ref<'hidden' | 'outline' | 'ready'>('hidden')
  const cubeCommand = ref<{ id: number; type: 'left' | 'right' | 'reset' | 'motion' }>({ id: 0, type: 'reset' })

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
