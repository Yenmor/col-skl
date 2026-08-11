<template>
  <header class="topbar">
    <div class="content-width flex items-center justify-between">
      <RouterLink to="/" class="brand">
        <span class="brand-mark">S</span>
        <span class="brand-text">大学生成长 Skill 共创场</span>
      </RouterLink>
      <div class="flex items-center gap-4">
        <span class="hidden sm:inline tiny">大学经验，换个方式留下来</span>
        <button class="btn-text" type="button" @click="$emit('action')">＋ 发布</button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
defineEmits<{ (e: 'action'): void }>()
</script>
