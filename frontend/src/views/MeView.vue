<template>
  <div class="page-shell">
    <TopBar />
    <main style="display:flex;flex-direction:column;align-items:center;justify-content:center;padding:80px 20px;gap:16px;">
      <div class="me-avatar">✦</div>
      <h2 style="font-size:20px;font-weight:700;">我的主页</h2>
      <p style="font-size:13px;color:var(--ink-soft);text-align:center;max-width:280px;line-height:1.7;">
        登录功能即将上线，目前你可以通过「社区」发布内容，通过「Skill 仓库」上传你的学长Skill。
      </p>
      <div style="display:flex;gap:12px;margin-top:8px;">
        <RouterLink to="/community" class="btn-secondary">去社区</RouterLink>
        <RouterLink to="/seniors" class="btn-primary">Skill 仓库</RouterLink>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import TopBar from '../components/common/TopBar.vue'
</script>
