<template>
  <section class="community">
    <header class="head">
      <h1>社区</h1>
      <div class="tabs">
        <button :class="{ active: tab === 'posts' }" @click="tab = 'posts'">帖子</button>
        <button :class="{ active: tab === 'distill' }" @click="tab = 'distill'">用户蒸馏</button>
        <button :class="{ active: tab === 'skills' }" @click="tab = 'skills'">召回</button>
        <button :class="{ active: tab === 'memories' }" @click="tab = 'memories'">我的记忆</button>
      </div>
    </header>

    <div v-if="store.error && store.usingFallback" class="warn-banner">
      离线模式：网络断开，已切到本地示例。恢复后会重新加载。
    </div>

    <!-- ==================== 帖子 tab ==================== -->
    <div v-if="tab === 'posts'">
      <div class="compose-card">
        <textarea v-model="newBody" placeholder="说点什么…（标题可选）" rows="3" class="compose-area"></textarea>
        <div class="compose-tools">
          <input v-model="newDomain" placeholder="#领域" class="topic-input" />
          <input v-model="newTitle" placeholder="标题（可选）" class="title-input" />
          <button @click="createPost" :disabled="!newBody.trim()" class="publish-btn">发布</button>
        </div>
      </div>

      <div v-if="store.loading" class="loading">加载中…</div>
      <div v-else-if="store.posts.length === 0" class="empty">还没有帖子，发第一条吧。</div>
      <div v-else class="feed">
        <article
          v-for="(p, i) in store.posts"
          :key="p.id"
          class="feed-item block-card"
          :class="`block-${i % 4}`"
          @click="openPost(p)"
        >
          <div class="cover" :style="coverStyle(p.coverColor)">
            <div class="cover-title">{{ p.title || extractTitle(p.excerpt) }}</div>
            <div class="cover-domain" v-if="p.domain">#{{ p.domain }}</div>
          </div>
          <div class="feed-body">
            <p class="feed-excerpt">{{ p.excerpt }}</p>
            <div class="feed-meta">
              <div class="author">
                <div class="avatar" :style="{ background: avatarColor(p.authorId || p.authorName) }">
                  {{ (p.authorName || '?').charAt(0) }}
                </div>
                <span class="author-name">{{ p.authorName }}</span>
                <span class="dot">·</span>
                <span class="time">{{ relTime(p.createdAt) }}</span>
              </div>
              <div class="stats">
                <span class="stat">❤ {{ p.likeCount }}</span>
                <span class="stat">💬 {{ p.commentCount }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>
    </div>

    <!-- ==================== 全屏帖子详情 modal（独立链） ==================== -->
    <div v-if="detailPost" class="post-modal" @click.self="closePost">
      <div class="post-modal-inner">
        <button class="modal-close" @click="closePost" aria-label="关闭">×</button>

        <div class="modal-cover" :style="coverStyle(detailPost.coverColor)">
          <div class="modal-title">{{ detailPost.title || extractTitle(detailPost.excerpt) }}</div>
          <div class="modal-domain" v-if="detailPost.domain">#{{ detailPost.domain }}</div>
        </div>

        <div class="modal-content">
          <div class="modal-author">
            <div class="avatar large" :style="{ background: avatarColor(detailPost.authorId || detailPost.authorName) }">
              {{ (detailPost.authorName || '?').charAt(0) }}
            </div>
            <div>
              <div class="modal-author-name">{{ detailPost.authorName }}</div>
              <div class="modal-time">{{ relTime(detailPost.createdAt) }}</div>
            </div>
          </div>

          <div class="modal-body">{{ detailPost.excerpt }}</div>

          <div class="modal-actions">
            <button @click="toggleLike(detailPost.id)" class="action-btn" :class="{ active: detailLiked }">
              <span class="icon">{{ detailLiked ? '❤' : '♡' }}</span>
              <span>{{ detailPost.likeCount }}</span>
            </button>
            <button class="action-btn">
              <span class="icon">💬</span>
              <span>{{ detailPost.commentCount }}</span>
            </button>
            <button class="action-btn">
              <span class="icon">↗</span>
              <span>分享</span>
            </button>
          </div>

          <div class="modal-comments">
            <h3>评论 ({{ comments.length }})</h3>
            <div v-if="comments.length === 0" class="empty">还没有评论，第一个发言吧。</div>
            <div v-for="c in comments" :key="c.id" class="comment-item">
              <div class="avatar small" :style="{ background: avatarColor(c.authorId || c.authorName) }">
                {{ (c.authorName || '?').charAt(0) }}
              </div>
              <div class="comment-body">
                <div class="comment-head">
                  <span class="comment-author">{{ c.authorName }}</span>
                  <span class="comment-time">{{ relTime(c.createdAt) }}</span>
                </div>
                <div class="comment-content">{{ c.body }}</div>
              </div>
            </div>
          </div>

          <div class="modal-composer">
            <div class="avatar small" :style="{ background: avatarColor(currentUserId) }">
              {{ (currentUserId || '?').charAt(0) }}
            </div>
            <input v-model="detailCommentText" placeholder="写评论…" class="modal-input" @keydown.enter="postDetailComment" />
            <button @click="postDetailComment" :disabled="!detailCommentText.trim()" class="modal-send">发送</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 用户蒸馏 tab ==================== -->
    <div v-else-if="tab === 'distill'" class="distill-tab">
      <h2>从社区发言蒸馏 Skill</h2>
      <p class="hint">输入目标用户 ID + 该用户的全部相关帖子 / 评论，按 metaskill 流程蒸馏成可审核的学长.Skill。</p>

      <div class="form">
        <div class="row"><label>用户 ID（必填）</label>
          <div class="id-row">
            <input v-model="distillUserId" placeholder="如：33333333-3333-4333-8333-333333333333" class="inp" />
            <button @click="autoFillFromActivity" :disabled="!distillUserId.trim() || autoFilling" class="btn-auto">
              {{ autoFilling ? '拉取中…' : '自动拉取发言' }}
            </button>
          </div>
        </div>
        <div class="row"><label>昵称</label><input v-model="distillDisplayName" placeholder="如：林学长" class="inp" /></div>
        <div class="row-2">
          <div><label>学校</label><input v-model="distillSchool" class="inp" /></div>
          <div><label>学院</label><input v-model="distillCollege" class="inp" /></div>
        </div>
        <div class="row-2">
          <div><label>专业</label><input v-model="distillMajor" class="inp" /></div>
          <div><label>毕业年</label><input v-model="distillYear" placeholder="如：2025" class="inp" /></div>
        </div>
        <div class="row"><label>领域</label><input v-model="distillDomain" placeholder="如：选课" class="inp" /></div>
        <div class="row"><label>触发词（逗号分隔）</label><input v-model="distillTriggersStr" placeholder="如：选课,方向课,课程负担" class="inp" /></div>
        <div class="row">
          <label>用户发言（每行一条标题 + body）</label>
          <textarea v-model="distillPostsStr" rows="6" class="inp" placeholder="【标题1】&#10;body1 内容&#10;&#10;【标题2】&#10;body2 内容"></textarea>
        </div>
        <button @click="runUserDistill" :disabled="distilling || !distillUserId.trim()" class="btn-primary">
          {{ distilling ? '蒸馏中…' : '开始蒸馏' }}
        </button>
      </div>

      <div v-if="userDistillResult" class="distill-result">
        <h3>✓ 蒸馏完成：{{ userDistillResult.seniorId }}</h3>
        <p>共 {{ userDistillResult.fragments.length }} 个片段已落库。</p>
        <div v-for="f in userDistillResult.fragments.slice(0, 5)" :key="f.id" class="fragment">
          <span class="kind" :class="`kind-${f.kind}`">{{ f.kind }}</span>
          <pre class="content">{{ f.content }}</pre>
        </div>
        <p v-if="userDistillResult.fragments.length > 5" class="more">…还有 {{ userDistillResult.fragments.length - 5 }} 个片段</p>
        <p class="hint">前往 <router-link to="/seniors">/seniors</router-link> 查看新 Skill。</p>
      </div>
    </div>

    <!-- ==================== 召回 tab ==================== -->
    <div v-else-if="tab === 'skills'" class="recall-tab">
      <h2>技能召回</h2>
      <p class="hint">输入问题，召回最匹配的学长 Skill。</p>
      <div class="recall-row">
        <input v-model="recallQuery" placeholder="例如：保研 / 选课 / 实习" class="recall-input" />
        <button @click="runRecall" :disabled="!recallQuery.trim() || recalling" class="btn-primary">
          {{ recalling ? '召回中…' : '召回' }}
        </button>
      </div>
      <div v-if="recallItems.length" class="recall-list">
        <div v-for="r in recallItems" :key="r.seniorId" class="recall-item">
          <div class="r-score">score: {{ r.score.toFixed(2) }}</div>
          <div class="r-text">{{ r.text }}</div>
          <div v-if="r.tags.length" class="r-tags">
            <span v-for="t in r.tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 我的记忆 tab ==================== -->
    <div v-else-if="tab === 'memories'" class="memories-tab">
      <h2>我的会话记忆</h2>
      <button @click="loadMemories" class="btn-primary btn-sm">刷新</button>
      <div v-if="memories.length === 0" class="empty">还没有记忆。先在首页发起一次对话。</div>
      <div v-else class="memory-list">
        <div v-for="m in memories" :key="m.memoryId" class="memory-card">
          <h4>{{ m.title || '未命名记忆' }}</h4>
          <div class="tags">
            <span v-for="t in m.tags" :key="t" class="tag">{{ t }}</span>
          </div>
          <div class="time">{{ relTime(m.createdAt) }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
import { useCommunityStore } from '../stores/communityStore';
import { skillsApi, memoriesApi, likesApi, commentsApi, postsApi } from '../services/api-v1';
import { getOrCreateUserId } from '../services/api-v1';
import type { DistillResult, SkillRecallItem, ChatMemoryDto, CommentDto, PostSummary } from '../types/api-v1';

const route = useRoute();
const store = useCommunityStore();
const tab = ref<'posts' | 'distill' | 'skills' | 'memories'>('posts');
const currentUserId = computed(() => getOrCreateUserId());

// post compose
const newTitle = ref('');
const newBody = ref('');
const newDomain = ref('');

// post detail modal
const detailPost = ref<PostSummary | null>(null);
const comments = ref<CommentDto[]>([]);
const detailCommentText = ref('');
const detailLiked = ref(false);

// 用户蒸馏
const distillUserId = ref('');
const distillDisplayName = ref('');
const distillSchool = ref('');
const distillCollege = ref('');
const distillMajor = ref('');
const distillYear = ref('');
const distillDomain = ref('');
const distillTriggersStr = ref('');
const distillPostsStr = ref('');
const distilling = ref(false);
const autoFilling = ref(false);
const userDistillResult = ref<DistillResult | null>(null);

async function autoFillFromActivity() {
  if (!distillUserId.value.trim()) return;
  autoFilling.value = true;
  try {
    const r = await fetch(`/api/v1/users/${encodeURIComponent(distillUserId.value.trim())}/activity`);
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const data = await r.json();
    distillDisplayName.value = data.displayName || '';
    // 把帖子与评论拼成可编辑文本
    const parts: string[] = [];
    for (const p of data.posts || []) {
      parts.push(`【${p.title}】\n${p.body}`);
      for (const c of p.comments || []) {
        parts.push(`【回复】\n${c.body}`);
      }
    }
    distillPostsStr.value = parts.join('\n\n');
    if (!distillDomain.value && data.posts?.length) {
      distillDomain.value = data.posts[0].domain || '';
    }
  } catch (e) {
    alert('拉取失败：' + (e as Error).message);
  } finally {
    autoFilling.value = false;
  }
}

// recall
const recallQuery = ref('');
const recalling = ref(false);
const recallItems = ref<SkillRecallItem[]>([]);

// memories
const memories = ref<ChatMemoryDto[]>([]);

function syncTabFromHash() {
  if (route.hash === '#distill-user') tab.value = 'distill';
}
watch(() => route.hash, syncTabFromHash, { immediate: true });

// =================== 帖子列表 ===================

function coverStyle(color: string | undefined) {
  const c = color || '#fde0e6';
  // 同色系渐变，制造封面感
  return {
    background: `linear-gradient(135deg, ${c} 0%, ${shade(c, 0.8)} 100%)`,
  };
}

function shade(hex: string, factor: number): string {
  // 简单 shade：让 hex 更深
  const m = hex.replace('#', '').match(/.{2}/g);
  if (!m) return hex;
  const [r, g, b] = m.map(x => Math.max(0, Math.min(255, Math.round(parseInt(x, 16) * factor))));
  return `rgb(${r}, ${g}, ${b})`;
}

function avatarColor(seed: string | undefined): string {
  if (!seed) return '#fde0e6';
  let h = 0;
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) | 0;
  const palette = ['#fde0e6', '#dceafd', '#e5f4dc', '#f9eedc', '#ece4fa', '#fde6d4', '#d4f0fa'];
  return palette[Math.abs(h) % palette.length];
}

function extractTitle(body: string | undefined): string {
  if (!body) return '';
  const first = body.split('\n').find(l => l.trim().length > 0) || '';
  return first.slice(0, 24);
}

async function createPost() {
  try {
    await store.create(newTitle.value.trim() || '（无标题）', newBody.value.trim(), newDomain.value.trim() || undefined);
    newTitle.value = '';
    newBody.value = '';
    newDomain.value = '';
  } catch (e) {
    alert('发布失败：' + (e as Error).message);
  }
}

// =================== 帖子详情 modal ===================

async function openPost(p: PostSummary) {
  detailPost.value = p;
  detailCommentText.value = '';
  detailLiked.value = false;
  document.body.style.overflow = 'hidden';
  try {
    // 拉取完整详情（含 body）
    const detail = await postsApi.get(p.id);
    detailPost.value = { ...p, excerpt: detail.body || p.excerpt };
  } catch (e) {
    // fallback 用 excerpt
  }
  try {
    comments.value = await commentsApi.list(p.id, { limit: 50 });
  } catch (e) {
    comments.value = [];
  }
}

function closePost() {
  detailPost.value = null;
  comments.value = [];
  document.body.style.overflow = '';
}

async function toggleLike(postId: string) {
  if (!detailPost.value) return;
  try {
    const r = await likesApi.like(postId);
    detailPost.value = { ...detailPost.value, likeCount: r.likeCount };
    detailLiked.value = r.liked;
    // 同步到列表
    store.like(postId);
  } catch (e) {
    alert('点赞失败：' + (e as Error).message);
  }
}

async function postDetailComment() {
  if (!detailPost.value || !detailCommentText.value.trim()) return;
  try {
    const c = await commentsApi.create(detailPost.value.id, {
      body: detailCommentText.value.trim(),
    });
    comments.value = [...comments.value, c];
    detailPost.value = { ...detailPost.value, commentCount: detailPost.value.commentCount + 1 };
    detailCommentText.value = '';
  } catch (e) {
    alert('评论失败：' + (e as Error).message);
  }
}

// =================== 用户蒸馏 ===================

async function runUserDistill() {
  if (!distillUserId.value.trim()) return;
  distilling.value = true;
  try {
    const posts = parsePosts(distillPostsStr.value);
    const triggers = distillTriggersStr.value.split(/[,，]/).map(s => s.trim()).filter(Boolean);
    const r = await fetch(`/api/v1/users/${encodeURIComponent(distillUserId.value.trim())}/distill`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        displayName: distillDisplayName.value.trim() || undefined,
        school: distillSchool.value.trim() || undefined,
        college: distillCollege.value.trim() || undefined,
        major: distillMajor.value.trim() || undefined,
        year: distillYear.value.trim() || undefined,
        domain: distillDomain.value.trim() || undefined,
        triggers: triggers.length ? triggers : undefined,
        posts,
      }),
    });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    userDistillResult.value = await r.json();
  } catch (e) {
    alert('蒸馏失败：' + (e as Error).message);
  } finally {
    distilling.value = false;
  }
}

function parsePosts(s: string): Array<{ id: string; title: string; body: string }> {
  const blocks = s.split(/\n\s*\n/).map(b => b.trim()).filter(Boolean);
  return blocks.map((b, i) => {
    const m = b.match(/【(.+?)】\s*([\s\S]*)/);
    if (m) return { id: `p_${Date.now()}_${i}`, title: m[1], body: m[2].trim() };
    return { id: `p_${Date.now()}_${i}`, title: `发言 ${i + 1}`, body: b };
  });
}

// =================== 召回 ===================

async function runRecall() {
  recalling.value = true;
  try {
    recallItems.value = await skillsApi.recall({ query: recallQuery.value, topK: 5 });
  } catch (e) {
    alert('召回失败：' + (e as Error).message);
  } finally {
    recalling.value = false;
  }
}

// =================== 记忆 ===================

async function loadMemories() {
  try {
    memories.value = await memoriesApi.listMine({ limit: 50 });
  } catch (e) {
    alert('加载记忆失败：' + (e as Error).message);
  }
}

function relTime(iso: string): string {
  const t = new Date(iso).getTime();
  const dt = Date.now() - t;
  if (dt < 60000) return '刚刚';
  if (dt < 3600000) return `${Math.floor(dt / 60000)} 分钟前`;
  if (dt < 86400000) return `${Math.floor(dt / 3600000)} 小时前`;
  return new Date(iso).toLocaleDateString('zh-CN');
}

onMounted(async () => {
  syncTabFromHash();
  await store.load();
});
</script>

<style scoped>
.community { padding: 24px 24px 80px; max-width: 720px; margin: 0 auto; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.head h1 { font-size: 26px; color: var(--ink); margin: 0; font-weight: 800; }
.tabs { display: flex; gap: 4px; background: var(--surface); border-radius: 999px; padding: 4px; }
.tabs button { padding: 6px 14px; border: none; background: transparent; cursor: pointer; color: var(--ink-2); border-radius: 999px; font-size: 14px; }
.tabs button.active { background: var(--pink); color: white; }
.warn-banner { padding: 10px 12px; background: #fff7e6; border: 1px solid #fde2a8; border-radius: 8px; color: #8a6a00; margin-bottom: 12px; }

/* ============== 沉浸式 compose ============== */
.compose-card { background: var(--surface); border-radius: 16px; padding: 14px 16px; margin-bottom: 18px; box-shadow: 0 2px 12px rgba(0,0,0,0.04); }
.compose-area { width: 100%; padding: 8px 0; border: 0; outline: 0; background: transparent; color: var(--ink); font-size: 15px; line-height: 1.6; resize: none; font-family: inherit; }
.compose-area::placeholder { color: var(--ink-faint, #999); }
.compose-tools { display: flex; gap: 8px; align-items: center; padding-top: 8px; border-top: 1px solid var(--border); }
.topic-input, .title-input { padding: 6px 10px; border-radius: 8px; border: 1px solid var(--border); background: var(--paper); color: var(--ink); font-size: 13px; }
.topic-input { width: 100px; }
.title-input { flex: 1; }
.publish-btn { padding: 7px 16px; border-radius: 999px; background: var(--pink); color: white; border: none; cursor: pointer; font-weight: 600; }
.publish-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* ============== 沉浸式 feed（方块瀑布）============== */
.feed { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
@media (min-width: 960px) {
  .feed { grid-template-columns: repeat(3, 1fr); }
}
.feed-item { background: var(--surface); border-radius: 18px; overflow: hidden; cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; box-shadow: 0 2px 14px rgba(0,0,0,0.04); display: flex; flex-direction: column; }
.feed-item:hover { transform: translateY(-2px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
/* 方块差异：不同封面高度制造错落 */
.block-0 .cover { min-height: 150px; }
.block-1 .cover { min-height: 190px; }
.block-2 .cover { min-height: 130px; }
.block-3 .cover { min-height: 210px; }
.cover { padding: 24px 18px; color: var(--ink, #222); display: flex; flex-direction: column; justify-content: flex-end; }
.cover-title { font-size: 18px; font-weight: 700; line-height: 1.4; text-shadow: 0 1px 2px rgba(255,255,255,0.5); display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.cover-domain { margin-top: 8px; font-size: 12px; opacity: 0.7; font-weight: 600; }
.feed-body { padding: 12px 16px 14px; flex: 1; display: flex; flex-direction: column; justify-content: space-between; }
.feed-excerpt { margin: 0 0 12px; color: var(--ink-2); font-size: 13px; line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.feed-meta { display: flex; justify-content: space-between; align-items: center; }
.author { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--ink-2); }
.avatar { display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; border-radius: 50%; color: white; font-weight: 600; font-size: 12px; flex: 0 0 auto; }
.avatar.large { width: 44px; height: 44px; font-size: 18px; }
.avatar.small { width: 32px; height: 32px; font-size: 13px; }
.author-name { color: var(--ink); font-weight: 500; }
.dot { opacity: 0.5; }
.stats { display: flex; gap: 12px; }
.stat { color: var(--ink-2); font-size: 13px; }

/* ============== 沉浸式 modal ============== */
.post-modal { position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,0.5); backdrop-filter: blur(8px); display: flex; align-items: stretch; justify-content: center; animation: fadeIn 0.2s ease; }
.post-modal-inner { position: relative; width: 100%; max-width: 720px; background: var(--paper); overflow-y: auto; animation: slideUp 0.25s ease; }
.modal-close { position: absolute; top: 12px; right: 12px; width: 36px; height: 36px; border-radius: 50%; background: rgba(255,255,255,0.85); border: 0; cursor: pointer; font-size: 22px; line-height: 1; z-index: 2; backdrop-filter: blur(4px); }
.modal-cover { padding: 60px 32px; min-height: 200px; display: flex; flex-direction: column; justify-content: center; }
.modal-title { font-size: 28px; font-weight: 800; line-height: 1.3; text-shadow: 0 2px 4px rgba(255,255,255,0.4); }
.modal-domain { margin-top: 12px; font-size: 14px; opacity: 0.7; font-weight: 600; }
.modal-content { padding: 20px 24px 80px; }
.modal-author { display: flex; gap: 12px; align-items: center; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid var(--border); }
.modal-author-name { font-weight: 600; color: var(--ink); }
.modal-time { font-size: 12px; color: var(--ink-2); margin-top: 2px; }
.modal-body { font-size: 16px; line-height: 1.8; color: var(--ink); white-space: pre-wrap; margin-bottom: 20px; }
.modal-actions { display: flex; gap: 8px; padding: 12px 0; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); margin-bottom: 24px; }
.action-btn { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); cursor: pointer; color: var(--ink); font-size: 14px; transition: all 0.15s; }
.action-btn:hover { background: var(--surface-2); }
.action-btn.active { color: var(--pink); border-color: var(--pink); }
.action-btn .icon { font-size: 16px; }

.modal-comments h3 { font-size: 16px; color: var(--ink); margin: 0 0 16px; }
.comment-item { display: flex; gap: 10px; padding: 12px 0; border-bottom: 1px solid var(--border); }
.comment-body { flex: 1; }
.comment-head { display: flex; gap: 8px; align-items: baseline; }
.comment-author { font-size: 13px; color: var(--ink); font-weight: 500; }
.comment-time { font-size: 11px; color: var(--ink-2); }
.comment-content { margin-top: 4px; color: var(--ink); font-size: 14px; line-height: 1.6; }
.modal-composer { display: flex; gap: 8px; align-items: center; padding: 12px 0; position: sticky; bottom: 0; background: var(--paper); border-top: 1px solid var(--border); margin-top: 16px; }
.modal-input { flex: 1; padding: 10px 14px; border-radius: 999px; border: 1px solid var(--border); background: var(--surface); color: var(--ink); font-size: 14px; outline: 0; }
.modal-input:focus { border-color: var(--pink); }
.modal-send { padding: 8px 18px; border-radius: 999px; background: var(--pink); color: white; border: 0; cursor: pointer; font-weight: 600; }
.modal-send:disabled { opacity: 0.4; cursor: not-allowed; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { transform: translateY(20px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }

.loading, .empty { padding: 60px 20px; text-align: center; color: var(--ink-2); }

/* ============== 蒸馏 / 召回 / 记忆 标签页 ============== */
.distill-tab, .recall-tab, .memories-tab { background: var(--surface); border-radius: 16px; padding: 20px; }
.form { display: flex; flex-direction: column; gap: 8px; }
.id-row { display: flex; gap: 8px; align-items: center; }
.id-row .inp { flex: 1; }
.btn-auto { padding: 8px 14px; border-radius: 8px; background: var(--surface-2); color: var(--ink); border: 1px solid var(--border); cursor: pointer; font-size: 13px; white-space: nowrap; }
.btn-auto:hover { background: var(--pink); color: white; border-color: var(--pink); }
.btn-auto:disabled { opacity: 0.5; cursor: not-allowed; }
.hint { color: var(--ink-2); font-size: 14px; margin: 0 0 12px; }
.row { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 8px; }
.row label, .row-2 label { color: var(--ink-2); font-size: 12px; }
.inp { width: 100%; padding: 8px 12px; border-radius: 8px; border: 1px solid var(--border); background: var(--paper); color: var(--ink); font-family: inherit; font-size: 14px; }
.btn-primary { padding: 8px 16px; border-radius: 8px; background: var(--pink); color: white; border: 0; cursor: pointer; font-weight: 600; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-sm { padding: 5px 12px; font-size: 13px; }
.distill-result { display: flex; flex-direction: column; gap: 8px; margin-top: 16px; }
.fragment { padding: 10px 12px; border: 1px solid var(--border); border-radius: 8px; background: var(--paper); }
.kind { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 11px; margin-right: 6px; font-weight: 600; }
.kind-PERSONA { background: #fce4ec; color: #c2185b; }
.kind-WORK { background: #e3f2fd; color: #1976d2; }
.kind-MEMORY { background: #fff3e0; color: #ef6c00; }
.kind-OTHER { background: #f5f5f5; color: #666; }
.content { color: var(--ink); font-size: 13px; line-height: 1.5; white-space: pre-wrap; }
.more { color: var(--ink-2); text-align: center; font-size: 13px; }
.recall-row { display: flex; gap: 8px; margin: 12px 0 20px; }
.recall-input { flex: 1; padding: 10px 14px; border-radius: 999px; border: 1px solid var(--border); background: var(--paper); color: var(--ink); }
.recall-list, .memory-list { display: flex; flex-direction: column; gap: 8px; }
.recall-item, .memory-card { padding: 12px 14px; border: 1px solid var(--border); border-radius: 10px; background: var(--paper); }
.r-score { color: var(--pink); font-size: 12px; font-weight: 600; }
.r-text { color: var(--ink); margin: 4px 0; }
.r-tags, .tags { display: flex; gap: 4px; flex-wrap: wrap; }
.tag { padding: 2px 6px; background: var(--surface-2); border-radius: 4px; font-size: 11px; color: var(--ink-2); }
.time { color: var(--ink-2); font-size: 12px; margin-top: 4px; }
.memory-card h4 { margin: 0 0 4px; color: var(--ink); }

@media (max-width: 720px) {
  .community { padding: 16px 16px 80px; }
  .head h1 { font-size: 22px; }
  .tabs button { padding: 5px 10px; font-size: 13px; }
  .row-2 { grid-template-columns: 1fr; }
  .post-modal-inner { max-width: 100%; }
  .modal-cover { padding: 40px 20px; min-height: 160px; }
  .modal-title { font-size: 22px; }
  .feed { grid-template-columns: 1fr; }
  .block-0 .cover, .block-1 .cover, .block-2 .cover, .block-3 .cover { min-height: 150px; }
}
</style>
