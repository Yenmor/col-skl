import axios from 'axios'
import { getOrCreateUserId } from './api-v1'
import { normalizeTrust } from '../skillFit'
import type {
  DistillSkillRequest,
  OwnMaterialsResult,
  SeniorSkill,
  SeniorSkillDetail,
  SkillCatalogResult,
  SkillGrowthFeedback,
  SkillGrowthKind,
  SkillRecallMatch,
  SkillSourceSummary,
  SkillTrustEvidence,
  TrustEvidenceItem,
} from '../skillsProfileTypes'

const BASE = import.meta.env.VITE_API_BASE ?? ''
const GROWTH_STORAGE_PREFIX = 'skillslab:demo-growth:'

const OFFLINE_INDEX: SeniorSkill[] = [
  { id: 'chen-baoyan', name: '陈学姐 · 保研', school: '山西大学', major: '计算机与信息技术学院 · 软件工程', year: '2024', domain: '保研', avatarFilename: 'chen.svg', source: 'manual' },
  { id: 'zhang-jingsai', name: '张学长 · 竞赛', school: '山西大学', major: '自动化与软件学院 · 软件工程', year: '2023', domain: '竞赛', avatarFilename: 'zhang.svg', source: 'manual' },
  { id: 'li-keyan', name: '李学长 · 科研', school: '山西大学', major: '数学科学学院 · 统计学', year: '2022', domain: '科研', avatarFilename: 'li.svg', source: 'manual' },
]

type ErrorEnvelope = { error?: { code?: string; message?: string; details?: unknown } }

export class SkillApiError extends Error {
  code: string
  status: number
  details?: unknown

  constructor(message: string, code = 'GENERAL_INTERNAL', status = 0, details?: unknown) {
    super(message)
    this.name = 'SkillApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

function authHeaders() {
  return { 'X-User-Id': getOrCreateUserId() }
}

function toApiError(error: unknown, fallback: string) {
  if (error instanceof SkillApiError) return error
  if (axios.isAxiosError(error)) {
    const envelope = error.response?.data as ErrorEnvelope | undefined
    return new SkillApiError(
      envelope?.error?.message || fallback,
      envelope?.error?.code,
      error.response?.status ?? 0,
      envelope?.error?.details,
    )
  }
  return new SkillApiError(error instanceof Error ? error.message : fallback)
}

function asString(value: unknown, fallback = '') {
  return typeof value === 'string' ? value : fallback
}

function asStrings(value: unknown) {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function normalizeSkill(raw: Record<string, unknown>): SeniorSkill {
  const trust = raw.trust && typeof raw.trust === 'object'
    ? normalizeTrust(raw.trust as Record<string, number>)
    : undefined
  return {
    id: asString(raw.id),
    name: asString(raw.name, '未命名 Skill'),
    school: asString(raw.school),
    major: asString(raw.major),
    year: asString(raw.year),
    domain: asString(raw.domain),
    avatarFilename: asString(raw.avatarFilename),
    source: asString(raw.source),
    ownerId: typeof raw.ownerId === 'string' ? raw.ownerId : null,
    visibility: raw.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC',
    layerId: typeof raw.layerId === 'string' ? raw.layerId : null,
    summary: asString(raw.summary) || undefined,
    version: asString(raw.version) || undefined,
    tags: asStrings(raw.tags),
    updatedAt: asString(raw.updatedAt) || asString(raw.createdAt) || undefined,
    createdAt: asString(raw.createdAt) || undefined,
    trust,
  }
}

function normalizeSources(raw: unknown): SkillSourceSummary {
  const value = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {}
  return {
    available: value.available === true,
    mappingCount: Number(value.mappingCount) || 0,
    threadCount: Number(value.threadCount) || 0,
    evidenceIds: asStrings(value.evidenceIds),
    missingReason: asString(value.missingReason) || null,
    verification: asString(value.verification) || 'MISSING',
  }
}

function optionalBoolean(value: unknown): boolean | undefined {
  return typeof value === 'boolean' ? value : undefined
}

function optionalCount(value: unknown): number | null | undefined {
  if (value === null) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? Math.round(parsed) : undefined
}

function optionalScore(value: unknown): number | null | undefined {
  const count = optionalCount(value)
  return typeof count === 'number' ? Math.min(100, count) : count
}

function normalizeTrustEvidenceItem(raw: unknown): TrustEvidenceItem | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const value = raw as Record<string, unknown>
  return {
    status: asString(value.status, 'MISSING'),
    label: asString(value.label),
    detail: asString(value.detail),
    score: optionalScore(value.score),
    confirmed: optionalBoolean(value.confirmed),
    authorized: optionalBoolean(value.authorized),
    aiAssisted: optionalBoolean(value.aiAssisted),
    aiScore: optionalScore(value.aiScore),
    authority: asString(value.authority) || undefined,
    authorityCount: optionalCount(value.authorityCount),
    likes: optionalCount(value.likes),
    downloads: optionalCount(value.downloads),
    comments: optionalCount(value.comments),
  }
}

function normalizeTrustEvidence(raw: unknown): SkillTrustEvidence | undefined {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined
  const value = raw as Record<string, unknown>
  return {
    overall: optionalScore(value.overall),
    level: asString(value.level) || null,
    summary: asString(value.summary) || null,
    source: normalizeTrustEvidenceItem(value.source),
    platform: normalizeTrustEvidenceItem(value.platform),
    community: normalizeTrustEvidenceItem(value.community),
  }
}

function normalizeDetail(raw: Record<string, unknown>): SeniorSkillDetail {
  const indexRaw = raw.index && typeof raw.index === 'object'
    ? raw.index as Record<string, unknown>
    : raw
  const manifestRaw = raw.manifest ?? raw.manifestJson
  const metaRaw = raw.meta ?? raw.metaJson
  return {
    index: normalizeSkill(indexRaw),
    skillMd: asString(raw.skillMd),
    workMd: asString(raw.workMd),
    personaMd: asString(raw.personaMd),
    manifest: parseObject(manifestRaw),
    meta: parseObject(metaRaw),
    sources: normalizeSources(raw.sources),
    trustEvidence: normalizeTrustEvidence(raw.trustEvidence),
  }
}

function parseObject(value: unknown): Record<string, unknown> {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>
  if (typeof value !== 'string' || !value.trim()) return {}
  try {
    const parsed = JSON.parse(value) as unknown
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

export async function fetchSeniors(): Promise<SkillCatalogResult> {
  try {
    const response = await axios.get(`${BASE}/api/v1/skills`, { headers: authHeaders(), timeout: 6000 })
    const body = response.data as { items?: unknown[]; facets?: { domains?: unknown; schools?: unknown } }
    return {
      items: (body.items ?? []).filter(item => item && typeof item === 'object').map(item => normalizeSkill(item as Record<string, unknown>)),
      facets: { domains: asStrings(body.facets?.domains), schools: asStrings(body.facets?.schools) },
      source: 'api',
    }
  } catch {
    try {
      const response = await axios.get<{ items?: unknown[] }>(`${BASE}/api/seniors`, { timeout: 3500 })
      const items = (response.data.items ?? []).filter(item => item && typeof item === 'object').map(item => normalizeSkill(item as Record<string, unknown>))
      return { items, facets: facetsFor(items), source: 'legacy' }
    } catch {
      return { items: OFFLINE_INDEX, facets: facetsFor(OFFLINE_INDEX), source: 'offline' }
    }
  }
}

function facetsFor(items: SeniorSkill[]) {
  return {
    domains: [...new Set(items.map(item => item.domain).filter(Boolean))],
    schools: [...new Set(items.map(item => item.school).filter(Boolean))],
  }
}

export async function fetchSenior(id: string): Promise<SeniorSkillDetail> {
  try {
    const response = await axios.get(`${BASE}/api/v1/skills/${encodeURIComponent(id)}`, { headers: authHeaders(), timeout: 8000 })
    return normalizeDetail(response.data as Record<string, unknown>)
  } catch (error) {
    throw toApiError(error, '无法读取这份 Skill 的真实详情')
  }
}

export async function recallSkills(query: string, topK = 20): Promise<Map<string, SkillRecallMatch>> {
  try {
    const response = await axios.post(`${BASE}/api/v1/skills/recall`, { query, topK }, { headers: authHeaders(), timeout: 10000 })
    const raw = Array.isArray(response.data) ? response.data : (response.data as { items?: unknown[] })?.items ?? []
    return new Map(raw.filter((item: unknown) => item && typeof item === 'object').map((item: Record<string, unknown>) => [
      asString(item.seniorId),
      { score: Number(item.score) || 0, text: asString(item.text), tags: asStrings(item.tags) },
    ]))
  } catch (error) {
    throw toApiError(error, '任务匹配暂时不可用')
  }
}

export async function uploadSeniorZip(file: File): Promise<SeniorSkill> {
  const form = new FormData()
  form.append('file', file)
  try {
    const response = await axios.post(`${BASE}/api/v1/skills/import`, form, { headers: authHeaders(), timeout: 30000 })
    const item = (response.data as { item?: Record<string, unknown> }).item
    if (!item) throw new SkillApiError('导入完成，但服务器没有返回 Skill 元数据', 'SKILL_IMPORT_INVALID')
    return normalizeSkill(item)
  } catch (error) {
    throw toApiError(error, '导入失败，请检查 zip 包结构')
  }
}

export async function downloadSkillBundle(id: string, filename: string) {
  try {
    const response = await axios.get(`${BASE}/api/v1/skills/${encodeURIComponent(id)}/bundle`, {
      headers: authHeaders(), responseType: 'blob', timeout: 30000,
    })
    const url = URL.createObjectURL(response.data as Blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${safeFilename(filename)}.zip`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    throw toApiError(error, 'Skill 包下载失败')
  }
}

function safeFilename(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '-').trim() || 'skill-bundle'
}

export async function fetchOwnMaterials(): Promise<OwnMaterialsResult> {
  try {
    const response = await axios.get<OwnMaterialsResult>(`${BASE}/api/v1/me/materials`, { headers: authHeaders(), timeout: 10000 })
    return {
      llmAvailable: response.data.llmAvailable === true,
      minimumThreads: Math.max(1, Number(response.data.minimumThreads) || 3),
      threads: Array.isArray(response.data.threads) ? response.data.threads : [],
    }
  } catch (error) {
    throw toApiError(error, '无法读取你的社区经验材料')
  }
}

export async function distillOwnSkill(body: DistillSkillRequest): Promise<SeniorSkillDetail> {
  try {
    const response = await axios.post(`${BASE}/api/v1/me/skills/distill`, body, { headers: authHeaders(), timeout: 120000 })
    const item = (response.data as { item?: Record<string, unknown> }).item
    if (!item) throw new SkillApiError('生成完成，但服务器没有返回草稿', 'DISTILL_GENERATION_FAILED')
    return normalizeDetail(item)
  } catch (error) {
    throw toApiError(error, '私有 Skill 草稿生成失败')
  }
}

export function avatarUrl(senior: SeniorSkill | Pick<SeniorSkill, 'id' | 'avatarFilename'>): string {
  const local: Record<string, string> = {
    'chen-baoyan': '/senior-chen.svg',
    'zhang-jingsai': '/senior-zhang.svg',
    'li-keyan': '/senior-li.svg',
  }
  if (!senior.avatarFilename) return local[senior.id] ?? ''
  return local[senior.id] ?? `${BASE}/api/seniors/${encodeURIComponent(senior.id)}/avatar?file=${encodeURIComponent(senior.avatarFilename)}`
}

function seededGrowthFeedback(skillId: string): SkillGrowthFeedback[] {
  return [
    {
      id: `${skillId}-demo-comment`, kind: 'COMMENT', authorName: '周同学',
      body: '步骤足够具体，我照着检查时很快找到了自己漏掉的一项。',
      createdAt: '2026-08-13T03:10:00Z', authorDecision: 'PENDING', platformDecision: 'PENDING', demo: true,
    },
    {
      id: `${skillId}-demo-suggestion-1`, kind: 'SUGGESTION', authorName: '林同学',
      body: '建议在适用边界里补充“政策或课程要求发生变化时，先回到学校官方通知重新核对”。',
      createdAt: '2026-08-13T03:20:00Z', authorDecision: 'ADOPTED', platformDecision: 'PENDING', demo: true,
    },
    {
      id: `${skillId}-demo-suggestion-2`, kind: 'SUGGESTION', authorName: '许同学',
      body: '可以增加一条完成后的复盘问题，帮助使用者把这次经验沉淀回自己的方法。',
      createdAt: '2026-08-13T03:30:00Z', authorDecision: 'PENDING', platformDecision: 'ADOPTED', demo: true,
    },
  ]
}

export function loadSkillGrowthFeedback(skillId: string): SkillGrowthFeedback[] {
  const fallback = seededGrowthFeedback(skillId)
  if (typeof localStorage === 'undefined') return fallback
  try {
    const raw = localStorage.getItem(`${GROWTH_STORAGE_PREFIX}${skillId}`)
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed as SkillGrowthFeedback[] : fallback
  } catch {
    return fallback
  }
}

function saveSkillGrowthFeedback(skillId: string, items: SkillGrowthFeedback[]) {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(`${GROWTH_STORAGE_PREFIX}${skillId}`, JSON.stringify(items))
  }
  return items
}

export function submitSkillGrowthFeedback(skillId: string, kind: SkillGrowthKind, body: string) {
  const items = loadSkillGrowthFeedback(skillId)
  return saveSkillGrowthFeedback(skillId, [{
    id: crypto.randomUUID(), kind, authorName: '演示同学', body: body.trim(),
    createdAt: new Date().toISOString(), authorDecision: 'PENDING', platformDecision: 'PENDING', demo: true,
  }, ...items])
}

export function adoptSkillGrowthFeedback(skillId: string, feedbackId: string, actor: 'author' | 'platform') {
  const items = loadSkillGrowthFeedback(skillId).map(item => item.id !== feedbackId ? item : {
    ...item,
    authorDecision: actor === 'author' ? 'ADOPTED' as const : item.authorDecision,
    platformDecision: actor === 'platform' ? 'ADOPTED' as const : item.platformDecision,
  })
  return saveSkillGrowthFeedback(skillId, items)
}
