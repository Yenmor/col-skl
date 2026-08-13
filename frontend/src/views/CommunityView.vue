<template>
  <div class="page-shell community-page">
    <TopBar action-label="发帖" @action="showCompose = true" />
    <header class="community-scene-header" :style="{ '--domain-color': activeDomain.color, '--domain-ink': activeDomain.ink }">
      <button type="button" class="community-back" @click="returnToCube"><ChevronLeft :size="16" />返回</button>
      <div class="community-scene-title">
        <small>{{ activeDomain.code }}</small>
        <h1>{{ activeDomain.name }}</h1>
        <p>分享这个方向的真实经历，问问过来人。</p>
      </div>
      <div class="community-scene-status"><i />{{ posts.length }} 条讨论</div>
    </header>

    <div class="community-forum-surface">
      <nav class="community-domains content-width" aria-label="按方向筛选">
        <button
          v-for="domain in skillDomains"
          :key="domain.id"
          :class="{ active: activeId === domain.id }"
          :style="{ '--domain-color': domain.color, '--domain-ink': domain.ink, '--domain-tint': domain.tint }"
          @click="selectDomain(domain.id)"
        ><span>{{ domain.glyph }}</span><strong>{{ domain.name }}</strong></button>
      </nav>

      <main class="community-layout content-width">
      <section class="community-feed">
        <header><div><span :style="{ background: activeDomain.color }" />最新讨论</div><small>{{ posts.length }} 条帖子</small></header>
        <div v-if="loading" class="feed-skeleton"><span v-for="n in 4" :key="n" /></div>
        <div v-else-if="posts.length === 0" class="feed-empty">还没有帖子，来发第一帖吧。</div>
        <div v-else class="post-list">
          <article v-for="post in posts" :key="post.id" class="post-card">
            <div class="post-author-row">
              <img class="author-avatar" :src="avatarFor(post.authorId)" :alt="post.authorName || '用户'" @error="onAvatarError" />
              <span><strong>{{ post.authorName }}</strong><small>{{ relativeTime(post.createdAt) }}<template v-if="post.domain"> · {{ post.domain }}</template></small></span>
              <button type="button" aria-label="更多操作"><MoreHorizontal :size="18" /></button>
            </div>
            <h2>{{ post.title }}</h2>
            <p>{{ post.excerpt }}</p>
            <footer><span><Heart :size="15" @click.stop="toggleLike(post)" :class="{ liked: likedPosts.has(post.id) }" />{{ post.likeCount }}</span><span><MessageCircle :size="15" />{{ post.commentCount }}</span><button type="button" @click="openPost(post)">查看评论 <ArrowUpRight :size="14" /></button></footer>
          </article>
        </div>
      </section>

      <aside class="community-context" :style="{ '--domain-color': activeDomain.color, '--domain-ink': activeDomain.ink, '--domain-tint': activeDomain.tint }">
        <span class="context-glyph">{{ activeDomain.glyph }}</span>
        <small>{{ activeDomain.code }}</small>
        <h2>{{ activeDomain.name }}</h2>
        <p>{{ activeDomain.description }}</p>
        <ul><li v-for="branch in activeDomain.branches" :key="branch.name"><i />{{ branch.name }}</li></ul>
        <RouterLink :to="`/seniors?domain=${activeDomain.name}`">查看这个方向的学长 Skill <ArrowUpRight :size="15" /></RouterLink>
      </aside>
      </main>
    </div>

    <Transition name="fade">
      <div v-if="showCompose" class="compose-overlay" @click.self="showCompose = false">
        <div class="compose-panel">
          <header><div><small>发布到「{{ activeDomain.name }}」</small><h2>发新帖</h2></div><button type="button" aria-label="关闭" @click="showCompose = false"><X :size="19" /></button></header>
          <input v-model="form.title" class="compose-input" placeholder="标题：例如「大二开始准备保研来得及吗」" maxlength="60" />
          <textarea v-model="form.body" class="compose-textarea" rows="6" placeholder="正文：分享你的经历、经验或问题" />
          <input v-model="form.authorName" class="compose-input" placeholder="你的昵称（不填则匿名）" maxlength="20" />
          <div class="compose-actions"><button class="btn-ghost" @click="showCompose = false">取消</button><button class="btn-primary" :disabled="submitting || !form.title.trim()" @click="submit">{{ submitting ? '发布中…' : '发帖' }}</button></div>
        </div>
      </div>
    </Transition>

    <div v-if="detailPost" class="post-modal" @click.self="closePost">
      <div class="post-modal-inner">
        <button class="modal-close" @click="closePost" aria-label="关闭">×</button>
        <div class="modal-cover" :style="{ background: `linear-gradient(135deg, ${activeDomain.color} 0%, ${activeDomain.tint} 100%)` }">
          <div class="modal-title">{{ detailPost.title }}</div>
          <div class="modal-domain" v-if="detailPost.domain">#{{ detailPost.domain }}</div>
        </div>
        <div class="modal-content">
          <div class="modal-author">
            <img class="avatar large" :src="avatarFor(detailPost.authorId)" :alt="detailPost.authorName" @error="onAvatarError" />
            <div>
              <div class="modal-author-name">{{ detailPost.authorName }}</div>
              <div class="modal-time">{{ relativeTime(detailPost.createdAt) }}</div>
            </div>
          </div>
          <div class="modal-body">{{ detailPost.excerpt }}</div>
          <div class="modal-actions">
            <button @click="toggleLike(detailPost)" class="action-btn" :class="{ active: likedPosts.has(detailPost.id) }">
              <span class="icon">{{ likedPosts.has(detailPost.id) ? '❤' : '♡' }}</span><span>{{ detailPost.likeCount }}</span>
            </button>
            <button class="action-btn"><span class="icon">💬</span><span>{{ detailPost.commentCount }}</span></button>
          </div>
          <div class="modal-comments">
            <h3>评论 ({{ comments.length }})</h3>
            <div v-if="comments.length === 0" class="empty">还没有评论，第一个发言吧。</div>
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <img class="avatar small" :src="avatarFor(c.authorId)" :alt="c.authorName || '用户'" @error="onAvatarError" />
              <div class="comment-body">
                <div class="comment-head">
                  <span class="comment-author">{{ c.authorName }}</span>
                  <span class="comment-time">{{ relativeTime(c.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ c.body }}</div>
              </div>
            </div>
          </div>
          <div class="modal-composer">
            <input v-model="detailCommentText" placeholder="写评论…" class="modal-input" @keydown.enter="postDetailComment" />
            <button @click="postDetailComment" :disabled="!detailCommentText.trim()" class="modal-send">发送</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { ArrowUpRight, ChevronLeft, Heart, MessageCircle, MoreHorizontal, X } from '@lucide/vue'
import { postsApi, commentsApi, likesApi, usersApi } from '../services/api-v1'
import type { PostSummary, CommentDto } from '../types/api-v1'
import { domainById, skillDomains, type DomainId } from '../domain'
import { avatarFor } from '../services/avatar'
import { useAbilitySpaceStore } from '../stores/abilitySpace'
import TopBar from '../components/common/TopBar.vue'

const route = useRoute()
const router = useRouter()
const abilitySpace = useAbilitySpaceStore()
const { activeId, communityPhase: phase } = storeToRefs(abilitySpace)
const posts = ref<PostSummary[]>([])
const loading = ref(true)
const showCompose = ref(false)
const submitting = ref(false)
const form = reactive({ title: '', body: '', authorName: '' })
const likedPosts = ref<Set<string>>(new Set())

const detailPost = ref<PostSummary | null>(null)
const comments = ref<CommentDto[]>([])
const detailCommentText = ref('')

function onAvatarError(event: Event) {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
  const fallback = document.createElement('span')
  fallback.className = 'avatar-fallback'
  const name = img.alt || '?'
  fallback.textContent = name.charAt(0)
  img.parentElement?.insertBefore(fallback, img.nextSibling)
}

const fromQuery = skillDomains.find(domain => domain.id === route.query.domain || domain.name === route.query.domain)?.id
const entryDomain = fromQuery ?? 'study'
if (phase.value === 'idle') abilitySpace.beginCommunity(entryDomain)
else if (fromQuery) abilitySpace.select(fromQuery)
const activeDomain = computed(() => domainById(activeId.value))

/** 方向 → 后端中文 domain 多值过滤（历史帖子 domain 含生活/教育等自由值） */
function domainQuery(id: DomainId): string {
  const map: Record<DomainId, string> = {
    study: '学习,保研,选课,生活,教育',
    research: '科研',
    competition: '竞赛',
    skills: '技能,求职,实习',
    custom: activeDomain.value.name === '自定义'
      ? '自定义,社团,公益,创业,交换'
      : activeDomain.value.name,
  }
  return map[id] ?? '学习'
}

async function fetchPostsForDomain() {
  loading.value = true
  try {
    const page = await postsApi.list({ domain: domainQuery(activeId.value), limit: 200 })
    posts.value = page.items
  } catch {
    posts.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  abilitySpace.openCommunity(entryDomain)
  await fetchPostsForDomain()
})

let exitTimer = 0
onBeforeRouteLeave((to) => {
  window.clearTimeout(exitTimer)
  if (to.path !== '/') {
    abilitySpace.finishCommunityExit()
    return
  }
  if (phase.value !== 'leaving') abilitySpace.leaveCommunity()
  exitTimer = window.setTimeout(() => abilitySpace.finishCommunityExit(), window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 40 : 760)
})

function selectDomain(id: DomainId) {
  abilitySpace.select(id)
  void router.replace({ path: '/community', query: { domain: id } })
  void fetchPostsForDomain()
}

function returnToCube() {
  abilitySpace.leaveCommunity()
  void router.push('/')
}

function relativeTime(iso: string) {
  if (!iso) return '刚刚'
  const t = new Date(iso).getTime()
  const dt = Date.now() - t
  if (dt < 60000) return '刚刚'
  if (dt < 3600000) return `${Math.floor(dt / 60000)} 分钟前`
  if (dt < 86400000) return `${Math.floor(dt / 3600000)} 小时前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

async function submit() {
  if (!form.title.trim() || submitting.value) return
  submitting.value = true
  try {
    if (form.authorName.trim()) {
      await usersApi.patchMe({ displayName: form.authorName.trim() }).catch(() => {})
    }
    const post = await postsApi.create({ title: form.title, body: form.body, domain: activeDomain.value.name })
    posts.value = [post, ...posts.value]
    Object.assign(form, { title: '', body: '', authorName: '' })
    showCompose.value = false
  } finally {
    submitting.value = false
  }
}

async function toggleLike(post: PostSummary) {
  try {
    const r = await likesApi.like(post.id)
    post.likeCount = r.likeCount
    if (r.liked) likedPosts.value = new Set([...likedPosts.value, post.id])
    else likedPosts.value = new Set([...likedPosts.value].filter(id => id !== post.id))
  } catch {
    /* 静默 */
  }
}

async function openPost(post: PostSummary) {
  detailPost.value = post
  detailCommentText.value = ''
  document.body.style.overflow = 'hidden'
  try {
    const detail = await postsApi.get(post.id)
    detailPost.value = { ...post, excerpt: detail.body || post.excerpt }
  } catch { /* 用 excerpt */ }
  try {
    comments.value = await commentsApi.list(post.id, { limit: 300 })
  } catch {
    comments.value = []
  }
}

function closePost() {
  detailPost.value = null
  comments.value = []
  document.body.style.overflow = ''
}

async function postDetailComment() {
  if (!detailPost.value || !detailCommentText.value.trim()) return
  try {
    const c = await commentsApi.create(detailPost.value.id, { body: detailCommentText.value.trim() })
    comments.value = [...comments.value, c]
    detailPost.value = { ...detailPost.value, commentCount: detailPost.value.commentCount + 1 }
    detailCommentText.value = ''
  } catch {
    /* 静默 */
  }
}
</script>
