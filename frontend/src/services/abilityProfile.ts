import { request } from './api-v1';
import { skillDomains } from '../domain';
import type { SkillRecallItem } from '../types/api-v1';

export interface EvidenceCounts {
  posts: number;
  comments: number;
  receivedLikes: number;
  receivedReplies: number;
  privateDrafts: number;
  publicSkills: number;
  total: number;
}

export interface AbilityProfile {
  userId: string;
  total: number;
  label?: string;
  domains: Array<{
    id: string;
    name: string;
    score: number;
    posts: number;
    likes: number;
    comments: number;
    sitePosts: number;
    seniors: number;
    branches: Array<{ name: string; note: string; score: number; evidence?: EvidenceCounts }>;
    evidence?: EvidenceCounts;
  }>;
  lowestDirection?: {
    domainId: string;
    domainName: string;
    branchName: string;
    score: number;
    evidenceCount: number;
  };
  recommendations?: SkillRecallItem[];
}

const PROFILE_BRANCHES_KEY = 'skillslab:profile-branches';

function readProfileBranches(): Record<string, Array<{ name: string; note: string }>> {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(PROFILE_BRANCHES_KEY);
    return raw ? JSON.parse(raw) as Record<string, Array<{ name: string; note: string }>> : {};
  } catch {
    return {};
  }
}

/**
 * 与 MeView 画像请求保持一致的参数：第五层名 + 五层（含 localStorage 自定义分支）定义。
 * App 预取与 MeView 刷新共用，避免两处参数漂移导致 custom 层匹配不上。
 */
export function buildProfileQuery(): string {
  const saved = readProfileBranches();
  const fifthLayer = skillDomains.find(domain => domain.id === 'custom')
    ?? skillDomains[skillDomains.length - 1];
  const directions = skillDomains.map(domain => ({
    id: domain.id,
    name: domain.name,
    branches: [
      ...domain.branches.map(branch => ({ name: branch.name, note: branch.note })),
      ...(saved[domain.id] ?? []).map(branch => ({ name: branch.name, note: branch.note })),
    ],
  }));
  const query = new URLSearchParams({
    fifthLayerName: fifthLayer.name,
    directions: JSON.stringify(directions),
  });
  return `/me/ability-profile?${query.toString()}`;
}

export function requestAbilityProfile(): Promise<AbilityProfile> {
  return request<AbilityProfile>('GET', buildProfileQuery());
}
