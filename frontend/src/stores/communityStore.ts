/**
 * Community store v1 — 重新实装，v1 优先 + 失败 fallback。
 */
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { postsApi, commentsApi, likesApi } from '../services/api-v1';
import type { PostSummary, CommentDto, LikeResult } from '../types/api-v1';

const FALLBACK_POSTS: PostSummary[] = [
  {
    id: 'mock-1',
    title: '保研流程怎么准备？',
    excerpt: '网络异常，以下为示例内容。',
    coverColor: '#fde0e6',
    authorId: 'mock-user-1',
    authorName: '示例用户',
    authorAvatar: null,
    domain: '保研',
    likeCount: 12,
    commentCount: 3,
    createdAt: new Date().toISOString(),
  },
  {
    id: 'mock-2',
    title: '计算机选课避坑',
    excerpt: '网络异常，以下为示例内容。',
    coverColor: '#dceafd',
    authorId: 'mock-user-2',
    authorName: '示例用户',
    authorAvatar: null,
    domain: '选课',
    likeCount: 8,
    commentCount: 1,
    createdAt: new Date(Date.now() - 86400000).toISOString(),
  },
];

export const useCommunityStore = defineStore('community', () => {
  const posts = ref<PostSummary[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const comments = ref<Record<string, CommentDto[]>>({});
  const usingFallback = ref(false);

  async function load() {
    loading.value = true;
    error.value = null;
    try {
      const page = await postsApi.list({ limit: 20 });
      posts.value = page.items;
      usingFallback.value = false;
    } catch (e) {
      // fallback（CLAUDE.md / AGENTS.md 强制：communityService 必须有 fallback）
      posts.value = FALLBACK_POSTS;
      usingFallback.value = true;
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function loadComments(postId: string) {
    try {
      const list = await commentsApi.list(postId, { limit: 50 });
      comments.value[postId] = list;
    } catch {
      comments.value[postId] = [];
    }
  }

  async function create(title: string, body: string, domain?: string): Promise<PostSummary> {
    const p = await postsApi.create({ title, body, domain });
    posts.value = [p, ...posts.value];
    return p;
  }

  async function postComment(postId: string, body: string, parentId?: string): Promise<CommentDto> {
    const c = await commentsApi.create(postId, { body, parentId });
    comments.value[postId] = [...(comments.value[postId] ?? []), c];
    return c;
  }

  async function like(postId: string): Promise<LikeResult> {
    const r = await likesApi.like(postId);
    // 更新本地计数
    const idx = posts.value.findIndex(p => p.id === postId);
    if (idx >= 0 && posts.value[idx]) {
      posts.value[idx] = { ...posts.value[idx], likeCount: r.likeCount };
    }
    return r;
  }

  async function unlike(postId: string): Promise<LikeResult> {
    const r = await likesApi.unlike(postId);
    const idx = posts.value.findIndex(p => p.id === postId);
    if (idx >= 0 && posts.value[idx]) {
      posts.value[idx] = { ...posts.value[idx], likeCount: r.likeCount };
    }
    return r;
  }

  return {
    posts, comments, loading, error, usingFallback,
    load, loadComments, create, postComment, like, unlike,
  };
});
