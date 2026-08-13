export type SkillVisibility = 'PUBLIC' | 'PRIVATE'

export interface SkillTrustMetrics {
  campusCoverage: number
  sourceTraceability: number
  methodCompleteness: number
  boundaryCompleteness: number
  packageCompleteness: number
  overall: number
}

export interface SkillRecallMatch {
  score: number
  text: string
  tags: string[]
}

export interface SeniorSkill {
  id: string
  name: string
  school: string
  major: string
  year: string
  domain: string
  avatarFilename: string
  source: string
  ownerId?: string | null
  visibility?: SkillVisibility
  layerId?: string | null
  summary?: string
  version?: string
  tags?: string[]
  updatedAt?: string
  createdAt?: string
  trust?: SkillTrustMetrics
}

export interface SkillSourceSummary {
  available: boolean
  mappingCount: number
  threadCount: number
  evidenceIds: string[]
  missingReason?: string | null
  verification?: 'PLATFORM_VERIFIED' | 'PACKAGE_DECLARED' | 'MISSING' | string
}

export type TrustEvidenceStatus = 'CONFIRMED' | 'PARTIAL' | 'MISSING' | 'NOT_TRACKED' | string

export interface TrustEvidenceItem {
  status: TrustEvidenceStatus
  label: string
  detail: string
  score?: number | null
  confirmed?: boolean
  authorized?: boolean
  aiAssisted?: boolean
  aiScore?: number | null
  authority?: string
  authorityCount?: number | null
  likes?: number | null
  downloads?: number | null
  comments?: number | null
}

export interface SkillTrustEvidence {
  overall?: number | null
  level?: string | null
  summary?: string | null
  source?: TrustEvidenceItem | null
  platform?: TrustEvidenceItem | null
  community?: TrustEvidenceItem | null
}

export interface SeniorSkillDetail {
  index: SeniorSkill
  skillMd: string
  workMd: string
  personaMd: string
  manifest: Record<string, unknown>
  meta: Record<string, unknown>
  sources: SkillSourceSummary
  trustEvidence?: SkillTrustEvidence
}

export type SkillGrowthKind = 'COMMENT' | 'SUGGESTION'
export type SkillGrowthDecision = 'PENDING' | 'ADOPTED'

export interface SkillGrowthFeedback {
  id: string
  kind: SkillGrowthKind
  authorName: string
  body: string
  createdAt: string
  authorDecision: SkillGrowthDecision
  platformDecision: SkillGrowthDecision
  demo: true
}

export interface SkillCatalogFacets {
  domains: string[]
  schools: string[]
}

export type SkillCatalogSource = 'api' | 'legacy' | 'offline'

export interface SkillCatalogResult {
  items: SeniorSkill[]
  facets: SkillCatalogFacets
  source: SkillCatalogSource
}

export interface OwnMaterialComment {
  id: string
  body: string
  authorId: string
  createdAt: string
}

export interface OwnMaterialThread {
  threadId: string
  title: string
  domain?: string | null
  post: {
    id: string
    body: string
    createdAt: string
  }
  comments: OwnMaterialComment[]
  ownedCommentIds: string[]
}

export interface OwnMaterialsResult {
  llmAvailable: boolean
  minimumThreads: number
  threads: OwnMaterialThread[]
}

export interface DistillSkillRequest {
  topic: string
  goal: string
  threadIds: string[]
  layerId?: string
  tags?: string[]
}
