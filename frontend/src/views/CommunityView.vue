<template>
  <div class="page-shell">
    <TopBar @action="showCompose = true" />

    <main class="content-width py-5">
      <div v-if="loading" class="empty-state"><p>加载中…</p></div>
      <div v-else class="waterfall">
        <article v-for="post in posts" :key="post.id" class="post-card">
          <div class="post-cover" :style="{ background: post.coverColor }">
            <p class="post-cover-title">{{ post.title }}</p>
          </div>
          <div class="post-body">
            <p class="post-excerpt">{{ post.excerpt }}</p>
            <div class="post-author-row">
              <span class="author-dot">{{ post.authorName.slice(0, 1) }}</span>
              <span style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ post.authorName }}</span>
              <span>♥ {{ post.likeCount }}</span>
            </div>
          </div>
        </article>
      </div>
    </main>

    <!-- Compose overlay -->
    <Transition name="fade">
      <div v-if="showCompose" class="compose-overlay" @click.self="showCompose = false">
        <div class="compose-panel">
          <h2 class="compose-heading">发布分享</h2>
          <input v-model="form.title" class="compose-input" placeholder="标题（一句话说清楚）" maxlength="60" />
          <textarea v-model="form.body" class="compose-textarea" rows="5" placeholder="分享你的经历、建议或问题…" />
          <input v-model="form.authorName" class="compose-input" placeholder="你的称呼（可匿名）" maxlength="20" />
          <div class="compose-actions">
            <button class="btn-ghost" @click="showCompose = false">取消</button>
            <button class="btn-primary" :disabled="submitting || !form.title.trim()" @click="submit">
              {{ submitting ? '发布中…' : '发布' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import type { CommunityPost } from '../types'
import { fetchPosts, createPost } from '../services/communityService'
import TopBar from '../components/common/TopBar.vue'

const posts = ref<CommunityPost[]>([])
const loading = ref(true)
const showCompose = ref(false)
const submitting = ref(false)
const form = reactive({ title: '', body: '', authorName: '' })

onMounted(async () => {
  posts.value = await fetchPosts()
  loading.value = false
})

async function submit() {
  if (!form.title.trim() || submitting.value) return
  submitting.value = true
  try {
    const p = await createPost({ title: form.title, body: form.body, authorName: form.authorName || '匿名用户' })
    posts.value.unshift(p)
    Object.assign(form, { title: '', body: '', authorName: '' })
    showCompose.value = false
  } catch { /* silent */ }
  finally { submitting.value = false }
}
</script>
