import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DomainId } from '../domain'

export const useAbilitySpaceStore = defineStore('ability-space', () => {
  const activeId = ref<DomainId>('skills')
  const expandedId = ref<DomainId | null>(null)
  const homeMode = ref<'chat' | 'cube'>('chat')
  const communityPhase = ref<'idle' | 'entering' | 'open' | 'leaving'>('idle')
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
    cubeCommand,
    select,
    showCube,
    showChat,
    prepareProfile,
    beginCommunity,
    openCommunity,
    leaveCommunity,
    finishCommunityExit,
    commandCube,
    toggleExpanded,
    closeExpanded,
  }
})
