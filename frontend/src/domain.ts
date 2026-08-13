export type DomainId = 'study' | 'research' | 'competition' | 'skills' | 'custom'

export interface SkillDomain {
  id: DomainId
  name: string
  code: string
  glyph: string
  color: string
  ink: string
  tint: string
  description: string
  score: number
  aliases: string[]
  branches: Array<{ name: string; note: string; score: number }>
}

export interface CustomLayerPreference {
  name: string
  description: string
}

export const CUSTOM_LAYER_STORAGE_KEY = 'skillslab:profile-layer'
export const DEFAULT_CUSTOM_LAYER: CustomLayerPreference = {
  name: '自定义',
  description: '从社区主题中选择你长期关注的方向，相关讨论、Skill 与能力证据会汇入这一层。',
}

export const customTopicPresets = [
  { name: '社团活动', description: '关注社团运营、活动组织与成员协作中的真实经验。' },
  { name: '志愿公益', description: '关注志愿服务、公益项目与社会参与中的行动经验。' },
  { name: '创业实践', description: '关注需求验证、团队协作与校园创业项目的实践经验。' },
  { name: '交换留学', description: '关注交换准备、跨文化学习与海外校园生活经验。' },
] as const

function storedCustomLayer(): CustomLayerPreference {
  if (typeof window === 'undefined') return DEFAULT_CUSTOM_LAYER
  try {
    const value = window.localStorage.getItem(CUSTOM_LAYER_STORAGE_KEY)
    if (!value) return DEFAULT_CUSTOM_LAYER
    const parsed = JSON.parse(value) as Partial<CustomLayerPreference>
    const savedName = parsed.name?.trim()
    // “生涯”曾是平台默认值，不应被当成用户主动设置的主题。
    if (!savedName || savedName === '生涯') {
      window.localStorage.setItem(CUSTOM_LAYER_STORAGE_KEY, JSON.stringify(DEFAULT_CUSTOM_LAYER))
      return DEFAULT_CUSTOM_LAYER
    }
    return {
      name: savedName,
      description: parsed.description?.trim() || `关注${savedName}主题中的讨论、实践与可复用经验。`,
    }
  } catch {
    return DEFAULT_CUSTOM_LAYER
  }
}

const initialCustomLayer = storedCustomLayer()

export const skillDomains: SkillDomain[] = [
  {
    id: 'study',
    name: '学习',
    code: 'STUDY',
    glyph: '∑',
    color: '#58d977',
    ink: '#146b36',
    tint: '#eafbef',
    description: '分享学习方法、课程经验与笔记。',
    score: 82,
    aliases: ['学习', '保研', '选课'],
    branches: [
      { name: '知识拆解', note: '课程与复杂概念', score: 88 },
      { name: '笔记重构', note: '复盘与知识连接', score: 81 },
      { name: '同伴讲解', note: '表达与答疑', score: 69 },
    ],
  },
  {
    id: 'research',
    name: '科研',
    code: 'RESEARCH',
    glyph: '◈',
    color: '#ffd447',
    ink: '#855700',
    tint: '#fff8da',
    description: '分享读论文、做实验的实操经验与踩坑。',
    score: 58,
    aliases: ['科研'],
    branches: [
      { name: '论文精读', note: '论证与证据定位', score: 72 },
      { name: '问题定义', note: '边界与研究价值', score: 54 },
      { name: '实验记录', note: '过程与复现', score: 47 },
    ],
  },
  {
    id: 'competition',
    name: '竞赛',
    code: 'COMPETE',
    glyph: '◆',
    color: '#ff835f',
    ink: '#a53022',
    tint: '#fff0eb',
    description: '组队、备赛与赛后复盘的经验分享。',
    score: 64,
    aliases: ['竞赛'],
    branches: [
      { name: '赛题拆解', note: '约束与目标识别', score: 78 },
      { name: '协作推进', note: '分工与节奏管理', score: 67 },
      { name: '答辩表达', note: '叙事与临场反馈', score: 52 },
    ],
  },
  {
    id: 'skills',
    name: '技能',
    code: 'SKILLS',
    glyph: '⌁',
    color: '#47cfff',
    ink: '#0d6985',
    tint: '#e8f9ff',
    description: '分享工具、设计、求职与实习的实战经验。',
    score: 76,
    aliases: ['技能', '求职', '实习'],
    branches: [
      { name: '数据表达', note: '分析与可视化', score: 84 },
      { name: '创意实践', note: '工具与项目交付', score: 76 },
      { name: '公开表达', note: '展示与沟通', score: 63 },
    ],
  },
  {
    id: 'custom',
    name: initialCustomLayer.name,
    code: 'CUSTOM',
    glyph: '＋',
    color: '#b98cff',
    ink: '#69409c',
    tint: '#f4edff',
    description: initialCustomLayer.description,
    score: 46,
    aliases: ['自定义', '社团', '公益', '创业', '交换', initialCustomLayer.name],
    branches: [
      { name: '主题脉络', note: '问题与方向判断', score: 58 },
      { name: '经验沉淀', note: '经历与方法整理', score: 48 },
      { name: '同伴反馈', note: '讨论与真实验证', score: 33 },
    ],
  },
]

export function saveCustomLayerPreference(preference: CustomLayerPreference) {
  const customLayer = skillDomains.find(domain => domain.id === 'custom')
  const name = preference.name.trim() || DEFAULT_CUSTOM_LAYER.name
  const description = preference.description.trim() || `关注${name}主题中的讨论、实践与可复用经验。`
  if (customLayer) {
    customLayer.name = name
    customLayer.description = description
    customLayer.aliases = [...new Set([...customLayer.aliases, name])]
  }
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(CUSTOM_LAYER_STORAGE_KEY, JSON.stringify({ name, description }))
    window.dispatchEvent(new CustomEvent('skillslab:custom-layer-change', { detail: { name, description } }))
  }
  return { name, description }
}

export function domainById(id?: string | null) {
  return skillDomains.find(domain => domain.id === id) ?? skillDomains[0]
}

export function domainForLabel(label?: string | null) {
  if (!label) return skillDomains[3]
  return skillDomains.find(domain => domain.aliases.some(alias => alias && label.includes(alias))) ?? skillDomains[3]
}

export function inferDomain(text: string) {
  const normalized = text.toLowerCase()
  if (/科研|论文|导师|实验|研究/.test(normalized)) return skillDomains[1]
  if (/竞赛|建模|比赛|组队|答辩/.test(normalized)) return skillDomains[2]
  if (skillDomains[4].aliases.some(alias => alias && normalized.includes(alias.toLowerCase()))) return skillDomains[4]
  if (/技能|简历|求职|实习|就业|面试|作品|工具/.test(normalized)) return skillDomains[3]
  return skillDomains[0]
}
