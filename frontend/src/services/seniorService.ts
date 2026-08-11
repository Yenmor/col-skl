import axios from 'axios'
import type { SeniorSkill, SeniorSkillDetail } from '../types'

const BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

const FALLBACK: SeniorSkill[] = [
  {
    id: 'chen-baoyan', name: '陈学姐 · 保研组', school: '山西大学',
    major: '计算机与信息技术学院 · 软件工程', year: '2024', domain: '保研',
    avatarFilename: 'chen.svg', source: 'manual', createdAt: '2026-08-12',
  },
  {
    id: 'zhang-jingsai', name: '张学长 · 竞赛组', school: '山西大学',
    major: '自动化与软件学院 · 软件工程', year: '2023', domain: '竞赛',
    avatarFilename: 'zhang.svg', source: 'manual', createdAt: '2026-08-12',
  },
  {
    id: 'li-keyan', name: '李学长 · 科研组', school: '山西大学',
    major: '数学科学学院 · 统计学', year: '2022', domain: '科研',
    avatarFilename: 'li.svg', source: 'manual', createdAt: '2026-08-12',
  },
]

export async function fetchSeniors(domain?: string, school?: string): Promise<SeniorSkill[]> {
  try {
    const res = await axios.get<{ items: SeniorSkill[] }>(`${BASE}/api/seniors`, {
      params: { domain: domain || undefined, school: school || undefined },
      timeout: 3500,
    })
    return res.data.items
  } catch {
    return FALLBACK.filter(item => (!domain || item.domain === domain) && (!school || item.school === school))
  }
}

export async function fetchSenior(id: string): Promise<SeniorSkillDetail | undefined> {
  try {
    const res = await axios.get<SeniorSkillDetail>(`${BASE}/api/seniors/${id}`, { timeout: 3500 })
    return res.data
  } catch {
    const index = FALLBACK.find(item => item.id === id)
    if (!index) return undefined
    return {
      index,
      skillMd: `# ${index.name}\n\n这是一份可调用的学长 Skill。\n\n## 适合回答\n\n${index.domain}方向的规划、经验与选择问题。`,
      workMd: '开发阶段暂以本地示例内容代替。',
      personaMd: '直接、具体、保持自己的视角。',
      manifestJson: JSON.stringify({ name: index.name, domain: index.domain }, null, 2),
      metaJson: JSON.stringify({ identity: { school: index.school, major: index.major, year: index.year } }, null, 2),
    }
  }
}

export function avatarUrl(senior: SeniorSkill): string {
  const local: Record<string, string> = {
    'chen-baoyan': '/senior-chen.svg',
    'zhang-jingsai': '/senior-zhang.svg',
    'li-keyan': '/senior-li.svg',
  }
  return local[senior.id] ?? `${BASE}/api/seniors/${senior.id}/avatar?file=${encodeURIComponent(senior.avatarFilename)}`
}

export async function uploadSeniorZip(file: File) {
  const form = new FormData()
  form.append('file', file)
  const res = await axios.post(`${BASE}/api/seniors/upload`, form)
  return res.data
}
