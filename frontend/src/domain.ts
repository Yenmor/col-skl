export type DomainId = 'study' | 'research' | 'competition' | 'skills'

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
]

export function domainById(id?: string | null) {
  return skillDomains.find(domain => domain.id === id) ?? skillDomains[0]
}

export function domainForLabel(label?: string | null) {
  if (!label) return skillDomains[3]
  return skillDomains.find(domain => domain.aliases.some(alias => label.includes(alias))) ?? skillDomains[3]
}

export function inferDomain(text: string) {
  const normalized = text.toLowerCase()
  if (/科研|论文|导师|实验|研究/.test(normalized)) return skillDomains[1]
  if (/竞赛|建模|比赛|组队|答辩/.test(normalized)) return skillDomains[2]
  if (/技能|简历|求职|实习|作品|工具/.test(normalized)) return skillDomains[3]
  return skillDomains[0]
}
