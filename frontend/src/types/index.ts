export interface SeniorSkill {
  id: string
  name: string
  school: string
  major: string
  year: string
  domain: string
  avatarFilename: string
  source: string
  createdAt: string
}

export interface SeniorSkillDetail {
  index: SeniorSkill
  skillMd: string
  workMd: string
  personaMd: string
  manifestJson: string
  metaJson: string
}

export interface CommunityPost {
  id: string
  authorName: string
  authorAvatar: string
  title: string
  excerpt: string
  body: string
  coverColor: string
  likeCount: number
  commentCount: number
  createdAt: string
}

export interface SeniorAnswer {
  seniorId: string
  name: string
  school: string
  major: string
  year: string
  content: string
}

export interface ChatResponse {
  sessionId: string
  answers: SeniorAnswer[]
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  answers?: SeniorAnswer[]
  isStreaming?: boolean
}

export const DOMAINS = ['全部', '保研', '竞赛', '科研', '求职']
