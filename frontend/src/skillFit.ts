import type { SeniorSkill, SkillRecallMatch, SkillTrustMetrics } from './skillsProfileTypes'

export const fitAxisLabels = ['校园场景覆盖', '来源可追溯', '方法完整', '边界完整', '包结构完整'] as const
export type FitScores = [number, number, number, number, number]

export interface SkillTrustProfile {
  scores: FitScores
  overall: number
  level: '结构完整' | '基本完整' | '仍需补充'
}

function score(value: unknown) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Math.max(0, Math.min(100, Math.round(parsed))) : 0
}

export function normalizeTrust(trust?: Partial<SkillTrustMetrics> | null): SkillTrustMetrics | undefined {
  if (!trust) return undefined
  const values = {
    campusCoverage: score(trust.campusCoverage),
    sourceTraceability: score(trust.sourceTraceability),
    methodCompleteness: score(trust.methodCompleteness),
    boundaryCompleteness: score(trust.boundaryCompleteness),
    packageCompleteness: score(trust.packageCompleteness),
  }
  const providedOverall = Number(trust.overall)
  return {
    ...values,
    overall: Number.isFinite(providedOverall)
      ? score(providedOverall)
      : Math.round(Object.values(values).reduce((sum, value) => sum + value, 0) / 5),
  }
}

export function skillTrustFor(skill: SeniorSkill): SkillTrustProfile | undefined {
  const trust = normalizeTrust(skill.trust)
  if (!trust) return undefined
  const scores: FitScores = [
    trust.campusCoverage,
    trust.sourceTraceability,
    trust.methodCompleteness,
    trust.boundaryCompleteness,
    trust.packageCompleteness,
  ]
  const level = trust.overall >= 80 ? '结构完整' : trust.overall >= 60 ? '基本完整' : '仍需补充'
  return { scores, overall: trust.overall, level }
}

export function recallMatchLabel(match?: SkillRecallMatch) {
  if (!match) return '尚未匹配'
  const percentage = Math.round(Math.max(0, Math.min(1, match.score)) * 100)
  if (percentage >= 75) return '高度匹配'
  if (percentage >= 45) return '可以迁移'
  return '关联较弱'
}

