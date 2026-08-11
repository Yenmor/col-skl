import axios from 'axios'
import type { CommunityPost } from '../types'

const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'
const FALLBACK: CommunityPost[] = [
  { id: 'p1', authorName: '一个还在摸索的人', authorAvatar: '', title: '大二下才开始准备保研，真的来得及吗？', excerpt: '我的绩点不算高，竞赛也只有一项，最近才开始认真了解推免。把我这周查到的信息整理了一下，希望给同样焦虑的人一点参考。', body: '', coverColor: '#fde0e6', likeCount: 128, commentCount: 24, createdAt: '2026-08-11' },
  { id: 'p2', authorName: '自软院某不知名选手', authorAvatar: '', title: '我们学院参加竞赛的一些真实情况', excerpt: '自动化与软件学院和计信院虽然都在计算机大类，但参加竞赛的组织、老师支持、校区都不太一样。', body: '', coverColor: '#dceafd', likeCount: 86, commentCount: 13, createdAt: '2026-08-10' },
  { id: 'p3', authorName: '刚进组的学弟', authorAvatar: '', title: '第一次联系导师，邮件到底怎么写？', excerpt: '看了很多模板，自己写的时候还是卡住了。后来问了几位学长，整理出一版不那么像群发的写法。', body: '', coverColor: '#e5f4dc', likeCount: 59, commentCount: 8, createdAt: '2026-08-09' },
  { id: 'p4', authorName: '数模队的第三个人', authorAvatar: '', title: '数学建模不是临时抱佛脚：我们的备赛时间表', excerpt: '从选题、找数据到写论文，给大家看一下我们队真实的三周安排。', body: '', coverColor: '#f9eedc', likeCount: 214, commentCount: 31, createdAt: '2026-08-08' },
]

export async function fetchPosts(): Promise<CommunityPost[]> {
  try {
    const res = await axios.get<{ items: CommunityPost[] }>(`${BASE}/api/community/posts`)
    return res.data.items.length ? res.data.items : FALLBACK
  } catch {
    return FALLBACK
  }
}

export async function createPost(body: { title: string; body: string; authorName: string }) {
  const res = await axios.post<CommunityPost>(`${BASE}/api/community/posts`, body)
  return res.data
}
