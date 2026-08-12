import { request } from './api-v1';

export interface AbilityProfile {
  userId: string;
  total: number;
  domains: Array<{
    id: string;
    name: string;
    score: number;
    posts: number;
    likes: number;
    comments: number;
    sitePosts: number;
    seniors: number;
    branches: Array<{ name: string; note: string; score: number }>;
  }>;
}

export function requestAbilityProfile(): Promise<AbilityProfile> {
  return request<AbilityProfile>('GET', '/me/ability-profile');
}
