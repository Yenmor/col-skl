<template>
  <div class="page-shell profile-page">
    <TopBar />
    <main class="profile-layout content-width" :class="{ focused: expandedId }" @keydown.esc="closeExpanded">
      <header class="profile-header">
        <div class="profile-person">
          <span>{{ displayName.slice(0, 1) }}</span>
          <div>
            <small>5 LAYERS / EVIDENCE</small>
            <h1>{{ displayName }}的能力画像</h1>
            <p>{{ profileEvidenceLabel }}</p>
          </div>
        </div>

        <div class="profile-radar-summary" aria-label="五层能力证据成熟度总览">
          <svg viewBox="0 0 120 120" role="img" :aria-label="radarAriaLabel">
            <polygon v-for="scale in [1, .75, .5, .25]" :key="scale" :points="radarPoints(scale)" class="radar-grid" />
            <line v-for="point in radarVertices" :key="`${point.x}-${point.y}`" x1="60" y1="60" :x2="point.x" :y2="point.y" />
            <polygon :points="abilityPolygon" class="radar-score" />
            <circle v-for="point in abilityVertices" :key="`${point.x}-${point.y}`" :cx="point.x" :cy="point.y" r="2.3" />
          </svg>
          <div>
            <span v-for="domain in profileDomains" :key="domain.id">
              <i :style="{ background: domain.color }" />{{ domain.name }} <b>{{ profileReady ? domain.score : '—' }}</b>
            </span>
          </div>
        </div>

        <div class="profile-total">
          <strong>{{ totalScore ?? '—' }}</strong>
          <span>/ 100<br />综合证据成熟度</span>
        </div>
      </header>

      <section class="profile-axis-panel" aria-label="五层能力证据成熟度">
        <div class="axis-scale" aria-hidden="true"><span>0</span><i /><span>50</span><i /><span>100</span></div>
        <div class="axis-list">
          <article
            v-for="(domain, index) in profileDomains"
            :key="domain.id"
            class="profile-axis"
            :data-axis-index="index"
            :class="{ expanded: expandedId === domain.id, muted: expandedId && expandedId !== domain.id }"
            :style="{ '--domain-color': domain.color, '--domain-ink': domain.ink, '--domain-tint': domain.tint, '--score': `${domain.score}%`, '--delay': `${index * 45}ms` }"
          >
            <button
              class="axis-main"
              type="button"
              :data-profile-axis="index"
              :aria-expanded="expandedId === domain.id"
              :aria-label="`${domain.name}层，证据成熟度${profileReady ? `${domain.score}分` : '尚未载入'}`"
              @click="toggle(domain.id)"
            >
              <span class="axis-glyph">{{ domain.glyph }}</span>
              <span class="axis-name"><strong>{{ domain.name }}</strong><small>{{ domain.code }}</small></span>
              <i class="axis-track" aria-hidden="true"><span /><span /><span /><span /><span /></i>
              <span class="axis-score">{{ profileReady ? domain.score : '—' }}<small v-if="profileReady">%</small></span>
              <ChevronDown :size="17" />
            </button>

            <div class="axis-detail">
              <div>
                <header>
                  <p>{{ domain.description }}</p>
                  <div class="axis-detail-actions">
                    <span>{{ domainEvidenceLabel(domain) }}</span>
                    <button type="button" @click="showDomainEvidence(domain.id)"><ScanSearch :size="14" />查看证据构成</button>
                    <RouterLink :to="{ path: '/community', query: { domain: domain.id } }">进入方向社区 <ArrowUpRight :size="14" /></RouterLink>
                  </div>
                </header>

                <ul>
                  <li v-for="branch in domain.branches" :key="branch.name">
                    <span><strong>{{ branch.name }}</strong><small>{{ branch.note }} · {{ branchEvidenceLabel(branch) }}</small></span>
                    <i><b :style="{ width: `${branch.score}%` }" /></i>
                    <em>{{ profileReady ? branch.score : '—' }}</em>
                    <button
                      v-if="branch.custom"
                      type="button"
                      :aria-label="`删除细分方向 ${branch.name}`"
                      title="删除这个自定义方向"
                      @click="removePersonalBranch(domain.id, branch.name)"
                    ><X :size="14" /></button>
                  </li>
                </ul>

                <div class="axis-personalize">
                  <form v-if="domain.id === 'custom' && layerEditorOpen" class="layer-editor" @submit.prevent="savePersonalLayer">
                    <fieldset>
                      <legend>从社区主题选择</legend>
                      <div class="topic-preset-list">
                        <button
                          v-for="topic in communityTopicOptions"
                          :key="topic.name"
                          type="button"
                          :class="{ selected: layerDraft.name === topic.name }"
                          @click="chooseTopic(topic)"
                        >{{ topic.name }}</button>
                      </div>
                    </fieldset>
                    <label><span>第五层名称</span><input v-model="layerDraft.name" maxlength="8" placeholder="自定义主题名称" /></label>
                    <label><span>主题说明</span><input v-model="layerDraft.description" maxlength="72" placeholder="这一层汇集哪些讨论和经验" /></label>
                    <div class="layer-editor-actions"><button type="button" @click="layerEditorOpen = false">取消</button><button type="submit">保存第五层</button></div>
                  </form>

                  <form v-if="branchEditorId === domain.id" class="branch-editor" @submit.prevent="addPersonalBranch(domain.id)">
                    <label><span>细分方向名称</span><input v-model="branchDraft.name" maxlength="10" placeholder="例如：用户研究" /></label>
                    <label><span>它关注什么</span><input v-model="branchDraft.note" maxlength="24" placeholder="例如：访谈与需求判断" /></label>
                    <div><button type="button" @click="branchEditorId = null">取消</button><button type="submit">加入我的画像</button></div>
                  </form>

                  <div v-if="branchEditorId !== domain.id && !(domain.id === 'custom' && layerEditorOpen)" class="axis-personalize-actions">
                    <button type="button" @click="openBranchEditor(domain.id)"><Plus :size="14" />新增细分方向</button>
                    <button v-if="domain.id === 'custom'" type="button" @click="openLayerEditor"><Pencil :size="14" />选择社区主题</button>
                    <span>自定义方向保存后，会按真实相关证据重新计算。</span>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </div>
      </section>

      <footer class="profile-footer">
        <BadgeCheck :size="17" />
        <span>分数表示当前可追溯证据的成熟度，不等同于你的真实能力。</span>
        <button type="button" @click="scrollToSection(skillsSectionRef)">查看我的 Skills <ArrowDown :size="15" /></button>
      </footer>

      <section ref="evidenceSectionRef" class="profile-evidence-section" aria-labelledby="profile-evidence-heading">
        <header class="profile-section-head">
          <div><h2 id="profile-evidence-heading">真实证据分析</h2><p>按发帖、评论、收到的互动和本人 Skills 计算。</p></div>
          <button v-if="profileError" type="button" :disabled="profileLoading" @click="refreshProfile"><RefreshCw :size="15" />重新载入</button>
        </header>

        <div v-if="profileLoading && !abilityProfile" class="profile-state" role="status">
          <LoaderCircle :size="20" class="profile-spinner" /><span><strong>正在读取能力证据</strong><small>画像载入后再显示分数和推荐。</small></span>
        </div>
        <div v-else-if="profileError && !abilityProfile" class="profile-state profile-state-error" role="alert">
          <CircleAlert :size="20" /><span><strong>暂时无法读取能力画像</strong><small>{{ profileError }}</small></span>
        </div>
        <div v-else-if="weakestDirection" class="profile-evidence-grid">
          <article class="profile-weakest" :style="domainStyle(weakestDirection.domainId)">
            <header>
              <span>{{ weakestDomain.glyph }}</span>
              <div><small>当前证据最少</small><h3>{{ weakestDirection.domainName }} · {{ weakestDirection.branchName || '整体' }}</h3></div>
              <strong>{{ weakestDirection.score }}<small>/100</small></strong>
            </header>
            <p>当前共 {{ weakestDirection.evidenceCount }} 项相关证据。补充真实经历后，成熟度会由后端重新计算。</p>
            <dl class="profile-evidence-counts">
              <div v-for="metric in weakestEvidenceMetrics" :key="metric.key"><dt>{{ metric.label }}</dt><dd>{{ metric.value }}<small>{{ metric.unit }} · 最高 {{ metric.cap }} 分</small></dd></div>
            </dl>
          </article>

          <article class="profile-recommendation">
            <header><div><small>社区真实召回</small><h3>适合补充这一方向的方法</h3></div><LoaderCircle v-if="recommendationLoading" :size="17" class="profile-spinner" /></header>
            <div v-if="recommendationLoading && !recommendation" class="profile-inline-state">正在匹配公共 Skill…</div>
            <div v-else-if="recommendation" class="profile-recommendation-body">
              <span class="profile-match-score">匹配度 {{ Math.round(recommendation.recall.score * 100) }}%</span>
              <h4>{{ recommendation.skill?.name || '已匹配到社区 Skill' }}</h4>
              <p>{{ recommendation.skill?.summary || recommendation.recall.text || '这份 Skill 暂未提供摘要。' }}</p>
              <div v-if="recommendation.recall.tags.length" class="profile-real-tags"><span v-for="tag in recommendation.recall.tags" :key="tag">{{ tag }}</span></div>
              <small v-if="recommendationDetailError" class="profile-inline-error">{{ recommendationDetailError }}</small>
              <div class="profile-recommendation-actions">
                <button type="button" @click="askWithSkill(recommendation.recall.seniorId, recommendation.skill?.name)"><MessageCircleQuestion :size="15" />调用这份 Skill</button>
                <RouterLink :to="`/seniors/${recommendation.recall.seniorId}`">查看详情 <ArrowUpRight :size="14" /></RouterLink>
              </div>
            </div>
            <div v-else-if="recommendationError" class="profile-inline-state profile-inline-error">{{ recommendationError }}</div>
            <div v-else class="profile-inline-state">当前没有召回到合适的公共 Skill。</div>
          </article>
        </div>
        <div v-else class="profile-state">
          <Inbox :size="20" /><span><strong>还没有可分析的证据</strong><small>在社区留下真实帖子或回复后，这里会开始形成画像。</small></span>
        </div>
      </section>

      <section ref="skillsSectionRef" class="profile-my-skills-section" aria-labelledby="profile-skills-heading">
        <header class="profile-section-head">
          <div><h2 id="profile-skills-heading">我的 Skills</h2><p>本人公开导入的 Skills 与自动生成的私有草稿。</p></div>
          <div class="profile-section-actions">
            <RouterLink to="/seniors/studio"><Plus :size="15" />沉淀新 Skill</RouterLink>
            <RouterLink to="/seniors"><Library :size="15" />公共仓库</RouterLink>
          </div>
        </header>

        <div v-if="skillsLoading && !mySkills.length" class="profile-state" role="status">
          <LoaderCircle :size="20" class="profile-spinner" /><span><strong>正在读取我的 Skills</strong><small>只显示当前用户有权访问的内容。</small></span>
        </div>
        <div v-else-if="skillsError && !mySkills.length" class="profile-state profile-state-error" role="alert">
          <CircleAlert :size="20" /><span><strong>暂时无法读取我的 Skills</strong><small>{{ skillsError }}</small></span><button type="button" @click="loadMySkills"><RefreshCw :size="15" />重试</button>
        </div>
        <div v-else-if="mySkills.length" class="profile-skill-list">
          <article v-for="skill in mySkills" :key="skill.id" class="profile-skill-row" :style="skillDomainStyle(skill)">
            <span class="profile-skill-mark">{{ skillDomain(skill).glyph }}</span>
            <div class="profile-skill-main">
              <div><RouterLink :to="`/seniors/${skill.id}`">{{ skill.name }}</RouterLink><span :class="skill.visibility === 'PRIVATE' ? 'is-private' : 'is-public'">{{ visibilityLabel(skill.visibility) }}</span></div>
              <p>{{ skill.summary || '该 Skill 未提供摘要。' }}</p>
              <small>{{ skillLayerLabel(skill) }} · v{{ skill.version || '未标注' }} · {{ formatUpdatedAt(skill.updatedAt) }}</small>
              <div v-if="skill.tags.length" class="profile-real-tags"><span v-for="tag in skill.tags.slice(0, 4)" :key="tag">{{ tag }}</span></div>
            </div>
            <div class="profile-skill-actions">
              <button type="button" title="调用这份 Skill" :aria-label="`调用 ${skill.name}`" @click="askWithSkill(skill.id, skill.name)"><MessageCircleQuestion :size="17" /></button>
              <button type="button" title="下载七件套 zip" :aria-label="`下载 ${skill.name}`" :disabled="downloadingId === skill.id" @click="downloadSkill(skill)">
                <LoaderCircle v-if="downloadingId === skill.id" :size="17" class="profile-spinner" /><Download v-else :size="17" />
              </button>
            </div>
          </article>
          <p v-if="skillsError" class="profile-list-warning"><CircleAlert :size="14" />{{ skillsError }}</p>
        </div>
        <div v-else class="profile-state">
          <FileArchive :size="20" /><span><strong>还没有自己的 Skill</strong><small>主动导入会公开；从经验沉淀生成的内容会先成为私有草稿。</small></span>
        </div>
      </section>
    </main>

    <Transition name="fade"><div v-if="toast" class="toast" role="status">{{ toast }}</div></Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowUpRight,
  BadgeCheck,
  ChevronDown,
  CircleAlert,
  Download,
  FileArchive,
  Inbox,
  Library,
  LoaderCircle,
  MessageCircleQuestion,
  Pencil,
  Plus,
  RefreshCw,
  ScanSearch,
  X,
} from '@lucide/vue'
import { customTopicPresets, saveCustomLayerPreference, skillDomains, type DomainId } from '../domain'
import TopBar from '../components/skillslab/SkillsProfileTopBar.vue'
import { getOrCreateUserId, postsApi, request, skillsApi, usersApi } from '../services/api-v1'
import { useAbilitySpaceStore } from '../stores/abilitySpace'
import type { SkillRecallItem } from '../types/api-v1'

type EvidenceCounts = {
  posts: number
  comments: number
  receivedLikes: number
  receivedReplies: number
  privateDrafts: number
  publicSkills: number
  total: number
}

type AbilityBranch = {
  name: string
  note: string
  score: number
  evidence?: EvidenceCounts
}

type AbilityDomain = {
  id: string
  name: string
  score: number
  posts: number
  likes: number
  comments: number
  sitePosts: number
  seniors: number
  branches: AbilityBranch[]
  evidence?: EvidenceCounts
}

type LowestDirection = {
  domainId: string
  domainName: string
  branchName: string
  score: number
  evidenceCount: number
}

type AbilityProfile = {
  userId: string
  total: number
  label?: string
  domains: AbilityDomain[]
  lowestDirection?: LowestDirection
  recommendations?: SkillRecallItem[]
}

type SkillTrust = {
  campusCoverage: number
  sourceTraceability: number
  methodCompleteness: number
  boundaryCompleteness: number
  packageCompleteness: number
  overall: number
}

type MySkill = {
  id: string
  name: string
  school: string
  major: string
  year: string
  domain: string
  avatarFilename: string
  source: string
  ownerId: string
  visibility: 'PUBLIC' | 'PRIVATE'
  layerId: string
  summary: string
  version: string
  tags: string[]
  createdAt: string
  updatedAt: string
  trust: SkillTrust
}

type SkillDetail = MySkill & {
  skillMd?: string
  workMd?: string
  personaMd?: string
  manifest?: Record<string, unknown>
  meta?: Record<string, unknown>
  sources?: Record<string, unknown>
}

type PersonalBranch = AbilityBranch & { custom?: boolean }
type ProfileDomain = Omit<(typeof skillDomains)[number], 'branches'> & { branches: PersonalBranch[]; evidence?: EvidenceCounts }
type TopicOption = { name: string; description: string }
type Recommendation = { recall: SkillRecallItem; skill: SkillDetail | null }

const PROFILE_BRANCHES_KEY = 'skillslab:profile-branches'
const EMPTY_EVIDENCE: EvidenceCounts = { posts: 0, comments: 0, receivedLikes: 0, receivedReplies: 0, privateDrafts: 0, publicSkills: 0, total: 0 }
const evidenceDefinitions = [
  { key: 'posts', label: '发帖', unit: '条', cap: 30 },
  { key: 'comments', label: '本人评论', unit: '条', cap: 20 },
  { key: 'receivedLikes', label: '收到点赞', unit: '次', cap: 20 },
  { key: 'receivedReplies', label: '收到回复', unit: '条', cap: 15 },
  { key: 'privateDrafts', label: '私有草稿', unit: '份', cap: 5 },
  { key: 'publicSkills', label: '公开 Skill', unit: '份', cap: 10 },
] as const

function readLocalValue<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') return fallback
  try {
    const value = window.localStorage.getItem(key)
    return value ? JSON.parse(value) as T : fallback
  } catch {
    return fallback
  }
}

const savedBranches = readLocalValue<Partial<Record<DomainId, PersonalBranch[]>>>(PROFILE_BRANCHES_KEY, {})
const profileDomains = ref<ProfileDomain[]>(skillDomains.map(domain => ({
  ...domain,
  score: 0,
  evidence: { ...EMPTY_EVIDENCE },
  branches: [
    ...domain.branches.map(branch => ({ ...branch, score: 0, evidence: { ...EMPTY_EVIDENCE } })),
    ...(savedBranches[domain.id] ?? []).map(branch => ({ ...branch, score: 0, evidence: { ...EMPTY_EVIDENCE }, custom: true })),
  ],
})))

const router = useRouter()
const abilitySpace = useAbilitySpaceStore()
const { expandedId } = storeToRefs(abilitySpace)
const displayName = ref('我')
const abilityProfile = ref<AbilityProfile | null>(null)
const profileLoading = ref(false)
const profileError = ref('')
const mySkills = ref<MySkill[]>([])
const skillsLoading = ref(false)
const skillsError = ref('')
const recommendation = ref<Recommendation | null>(null)
const recommendationLoading = ref(false)
const recommendationError = ref('')
const recommendationDetailError = ref('')
const downloadingId = ref('')
const evidenceDomainId = ref<DomainId | null>(null)
const branchEditorId = ref<DomainId | null>(null)
const layerEditorOpen = ref(false)
const branchDraft = reactive({ name: '', note: '' })
const customDomain = profileDomains.value.find(domain => domain.id === 'custom')!
const layerDraft = reactive({ name: customDomain.name, description: customDomain.description })
const communityTopicOptions = ref<TopicOption[]>(customTopicPresets.map(topic => ({ ...topic })))
const evidenceSectionRef = ref<HTMLElement | null>(null)
const skillsSectionRef = ref<HTMLElement | null>(null)
const toast = ref('')
let toastTimer = 0

const profileReady = computed(() => Boolean(abilityProfile.value))
const totalScore = computed(() => abilityProfile.value?.total ?? null)
const evidenceTotal = computed(() => abilityProfile.value?.domains.reduce((total, domain) => total + evidenceOf(domain).total, 0) ?? 0)
const profileEvidenceLabel = computed(() => {
  if (profileLoading.value && !abilityProfile.value) return '正在读取真实证据…'
  if (profileError.value && !abilityProfile.value) return '能力画像暂时不可用'
  if (!abilityProfile.value) return '尚未载入能力证据'
  return `${evidenceTotal.value} 项可追溯证据 · ${profileDomains.value.length} 个能力层`
})
const radarAriaLabel = computed(() => profileReady.value
  ? profileDomains.value.map(domain => `${domain.name} ${domain.score}`).join('，')
  : '能力证据尚未载入')
const radarVertices = computed(() => radarCoordinates(profileDomains.value.map(() => 1)))
const abilityVertices = computed(() => radarCoordinates(profileDomains.value.map(domain => domain.score / 100)))
const abilityPolygon = computed(() => abilityVertices.value.map(point => `${point.x},${point.y}`).join(' '))

const weakestDirection = computed<LowestDirection | null>(() => {
  if (!abilityProfile.value) return null
  if (evidenceDomainId.value) {
    const domain = profileDomains.value.find(item => item.id === evidenceDomainId.value)
    if (domain) {
      const branch = [...domain.branches].sort((a, b) => a.score - b.score)[0]
      return {
        domainId: domain.id,
        domainName: domain.name,
        branchName: branch?.name ?? '',
        score: branch?.score ?? domain.score,
        evidenceCount: evidenceOf(branch ?? domain).total,
      }
    }
  }
  if (abilityProfile.value.lowestDirection) return abilityProfile.value.lowestDirection
  const domain = [...profileDomains.value].sort((a, b) => a.score - b.score)[0]
  if (!domain) return null
  const branch = [...domain.branches].sort((a, b) => a.score - b.score)[0]
  return {
    domainId: domain.id,
    domainName: domain.name,
    branchName: branch?.name ?? '',
    score: branch?.score ?? domain.score,
    evidenceCount: evidenceOf(branch ?? domain).total,
  }
})

const weakestDomain = computed(() => domainFor((weakestDirection.value?.domainId as DomainId | undefined) ?? 'study'))
const weakestEvidence = computed(() => {
  const direction = weakestDirection.value
  if (!direction) return EMPTY_EVIDENCE
  const domain = profileDomains.value.find(item => item.id === direction.domainId || item.name === direction.domainName)
  const branch = domain?.branches.find(item => item.name === direction.branchName)
  return evidenceOf(branch ?? domain)
})
const weakestEvidenceMetrics = computed(() => evidenceDefinitions.map(definition => ({
  ...definition,
  value: weakestEvidence.value[definition.key],
})))

function radarCoordinates(values: number[]) {
  return values.map((value, index) => {
    const angle = -Math.PI / 2 + index * Math.PI * 2 / values.length
    return { x: 60 + Math.cos(angle) * 46 * value, y: 60 + Math.sin(angle) * 46 * value }
  })
}

function radarPoints(scale: number) {
  return radarCoordinates(profileDomains.value.map(() => scale)).map(point => `${point.x},${point.y}`).join(' ')
}

function evidenceOf(value?: { evidence?: EvidenceCounts } | null): EvidenceCounts {
  return value?.evidence ? { ...EMPTY_EVIDENCE, ...value.evidence } : EMPTY_EVIDENCE
}

function domainFor(id: DomainId) {
  return profileDomains.value.find(domain => domain.id === id) ?? profileDomains.value[0]!
}

function domainStyle(id: string) {
  const domain = profileDomains.value.find(item => item.id === id) ?? profileDomains.value[0]!
  return { '--domain-color': domain.color, '--domain-ink': domain.ink, '--domain-tint': domain.tint }
}

function domainEvidenceLabel(domain: ProfileDomain) {
  if (!profileReady.value) return '证据尚未载入'
  const total = evidenceOf(domain).total
  return total ? `${total} 项真实证据` : '暂无相关证据'
}

function branchEvidenceLabel(branch: PersonalBranch) {
  if (!profileReady.value) return '证据尚未载入'
  const total = evidenceOf(branch).total
  return total ? `${total} 项证据` : '暂无证据'
}

function toggle(id: DomainId) {
  abilitySpace.toggleExpanded(id)
}

function closeExpanded() {
  abilitySpace.closeExpanded()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') closeExpanded()
}

function showDomainEvidence(id: DomainId) {
  evidenceDomainId.value = id
  scrollToSection(evidenceSectionRef.value)
  const direction = weakestDirection.value
  if (direction && abilityProfile.value) void loadRecommendationForDirection(direction)
}

function scrollToSection(target: HTMLElement | null) {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  target?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' })
}

function persistPersonalProfile() {
  const customBranches = Object.fromEntries(profileDomains.value.map(domain => [
    domain.id,
    domain.branches.filter(branch => branch.custom).map(branch => ({ name: branch.name, note: branch.note, score: 0, custom: true })),
  ]))
  window.localStorage.setItem(PROFILE_BRANCHES_KEY, JSON.stringify(customBranches))
}

function chooseTopic(topic: TopicOption) {
  layerDraft.name = topic.name
  layerDraft.description = topic.description
}

function openBranchEditor(id: DomainId) {
  branchEditorId.value = id
  layerEditorOpen.value = false
  branchDraft.name = ''
  branchDraft.note = ''
}

function addPersonalBranch(id: DomainId) {
  const name = branchDraft.name.trim()
  const note = branchDraft.note.trim()
  if (!name || !note) {
    showToast('请填写细分方向名称和说明')
    return
  }
  const domain = domainFor(id)
  if (domain.branches.some(branch => branch.name === name)) {
    showToast('这个细分方向已经存在')
    return
  }
  domain.branches.push({ name, note, score: 0, evidence: { ...EMPTY_EVIDENCE }, custom: true })
  persistPersonalProfile()
  branchEditorId.value = null
  showToast(`已加入“${name}”，正在重新计算`)
  void refreshProfile()
}

function removePersonalBranch(id: DomainId, name: string) {
  const domain = domainFor(id)
  domain.branches = domain.branches.filter(branch => branch.name !== name)
  persistPersonalProfile()
  showToast(`已移除“${name}”，正在重新计算`)
  void refreshProfile()
}

function openLayerEditor() {
  const domain = domainFor('custom')
  layerDraft.name = domain.name
  layerDraft.description = domain.description
  layerEditorOpen.value = true
  branchEditorId.value = null
}

function savePersonalLayer() {
  const name = layerDraft.name.trim()
  if (!name) {
    showToast('请为第五层填写名称')
    return
  }
  const domain = domainFor('custom')
  const preference = saveCustomLayerPreference({ name, description: layerDraft.description })
  domain.name = preference.name
  domain.description = preference.description
  layerEditorOpen.value = false
  showToast('第五层已更新，正在重新计算')
  void refreshProfile()
}

function profileRequestUrl() {
  const fifthLayer = domainFor('custom')
  const directions = profileDomains.value.map(domain => ({
    id: domain.id,
    name: domain.name,
    branches: domain.branches.map(branch => ({ name: branch.name, note: branch.note })),
  }))
  const query = new URLSearchParams({
    fifthLayerName: fifthLayer.name,
    directions: JSON.stringify(directions),
  })
  return `/me/ability-profile?${query.toString()}`
}

function remoteDomainFor(local: ProfileDomain, remote: AbilityProfile) {
  return remote.domains.find(domain => domain.id === local.id
    || domain.name === local.name
    || local.aliases.some(alias => alias && domain.name.includes(alias)))
}

function applyAbilityProfile(remote: AbilityProfile) {
  abilityProfile.value = remote
  profileDomains.value = profileDomains.value.map(local => {
    const found = remoteDomainFor(local, remote)
    if (!found) return { ...local, score: 0, evidence: { ...EMPTY_EVIDENCE }, branches: local.branches.map(branch => ({ ...branch, score: 0, evidence: { ...EMPTY_EVIDENCE } })) }
    const remoteBranches: PersonalBranch[] = found.branches.map(branch => ({
      ...branch,
      evidence: evidenceOf(branch),
      custom: local.branches.find(item => item.name === branch.name)?.custom,
    }))
    const missingCustom = local.branches
      .filter(branch => branch.custom && !remoteBranches.some(item => item.name === branch.name))
      .map(branch => ({ ...branch, score: 0, evidence: { ...EMPTY_EVIDENCE } }))
    const sharedDomain = skillDomains.find(domain => domain.id === local.id)
    if (sharedDomain) sharedDomain.score = found.score
    return { ...local, name: found.name || local.name, score: found.score, evidence: evidenceOf(found), branches: [...remoteBranches, ...missingCustom] }
  })
}

async function refreshProfile() {
  profileLoading.value = true
  profileError.value = ''
  try {
    const remote = await request<AbilityProfile>('GET', profileRequestUrl())
    applyAbilityProfile(remote)
    await loadRecommendation(remote)
  } catch (error) {
    profileError.value = friendlyError(error, '能力画像服务暂时不可用，请稍后重试。')
  } finally {
    profileLoading.value = false
  }
}

async function loadRecommendation(profile: AbilityProfile) {
  recommendationLoading.value = true
  recommendationError.value = ''
  recommendationDetailError.value = ''
  recommendation.value = null
  try {
    let recall = profile.recommendations?.[0]
    if (!recall) {
      const direction = profile.lowestDirection ?? weakestDirection.value
      if (direction) {
        recall = await recallForDirection(direction)
      }
    }
    if (!recall) return
    recommendation.value = { recall, skill: null }
    try {
      const skill = await request<SkillDetail>('GET', `/skills/${encodeURIComponent(recall.seniorId)}`)
      recommendation.value = { recall, skill }
    } catch {
      recommendationDetailError.value = 'Skill 详情暂时无法读取，仍可按召回结果定向调用。'
    }
  } catch (error) {
    recommendationError.value = friendlyError(error, '暂时无法从公共仓库召回推荐。')
  } finally {
    recommendationLoading.value = false
  }
}

async function recallForDirection(direction: LowestDirection) {
  const items = await skillsApi.recall({
    query: `${direction.domainName} ${direction.branchName} 方法经验`,
    topK: 1,
    domain: direction.domainName,
  })
  return items[0]
}

async function loadRecommendationForDirection(direction: LowestDirection) {
  recommendationLoading.value = true
  recommendationError.value = ''
  recommendationDetailError.value = ''
  recommendation.value = null
  try {
    const recall = await recallForDirection(direction)
    if (!recall) return
    recommendation.value = { recall, skill: null }
    try {
      const skill = await request<SkillDetail>('GET', `/skills/${encodeURIComponent(recall.seniorId)}`)
      recommendation.value = { recall, skill }
    } catch {
      recommendationDetailError.value = 'Skill 详情暂时无法读取，仍可按召回结果定向调用。'
    }
  } catch (error) {
    recommendationError.value = friendlyError(error, '暂时无法从公共仓库召回推荐。')
  } finally {
    recommendationLoading.value = false
  }
}

async function loadMySkills() {
  skillsLoading.value = true
  skillsError.value = ''
  try {
    const response = await request<{ items: MySkill[] }>('GET', '/me/skills')
    mySkills.value = Array.isArray(response.items) ? response.items : []
  } catch (error) {
    skillsError.value = friendlyError(error, '我的 Skills 服务暂时不可用，请稍后重试。')
  } finally {
    skillsLoading.value = false
  }
}

function skillDomain(skill: MySkill) {
  return profileDomains.value.find(domain => domain.id === skill.layerId
    || domain.name === skill.domain
    || domain.aliases.some(alias => alias && skill.domain?.includes(alias))) ?? profileDomains.value[3]!
}

function skillDomainStyle(skill: MySkill) {
  return domainStyle(skillDomain(skill).id)
}

function skillLayerLabel(skill: MySkill) {
  return skillDomain(skill).name
}

function visibilityLabel(visibility: MySkill['visibility']) {
  return visibility === 'PRIVATE' ? '私有草稿' : '公开'
}

function formatUpdatedAt(value: string) {
  if (!value) return '更新时间未标注'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)} 更新`
}

function askWithSkill(id: string, name?: string) {
  sessionStorage.setItem('skillslab:senior-id', id)
  sessionStorage.setItem('skillslab:question-draft', name ? `请使用「${name}」帮我处理：` : '请使用这份 Skill 帮我处理：')
  void router.push('/')
}

async function downloadSkill(skill: MySkill) {
  downloadingId.value = skill.id
  try {
    const response = await fetch(`/api/v1/skills/${encodeURIComponent(skill.id)}/bundle`, {
      headers: { 'X-User-Id': getOrCreateUserId() },
    })
    if (!response.ok) {
      let message = `下载失败（${response.status}）`
      try {
        const payload = await response.json() as { error?: { message?: string } }
        message = payload.error?.message || message
      } catch {
        // 非 JSON 错误响应沿用状态码提示。
      }
      throw new Error(message)
    }
    const blob = await response.blob()
    if (!blob.size) throw new Error('下载内容为空')
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${safeFilename(skill.name)}.zip`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    showToast('七件套 zip 已下载')
  } catch (error) {
    showToast(friendlyError(error, '下载失败，请稍后重试。'))
  } finally {
    downloadingId.value = ''
  }
}

function safeFilename(value: string) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001F]/g, '_').trim() || 'skill-bundle'
}

function friendlyError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message.trim() : ''
  if (!message || /network error|failed to fetch|request failed with status code|timeout|econnrefused/i.test(message)) return fallback
  return message
}

function showToast(message: string) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
}

async function loadCommunityTopics() {
  try {
    const response = await postsApi.list({ limit: 40 })
    const defaultNames = new Set(skillDomains.slice(0, 4).flatMap(domain => [domain.name, ...domain.aliases]))
    const topics = [...new Set(response.items
      .map(post => post.domain?.trim())
      .filter((name): name is string => Boolean(name) && !defaultNames.has(name!)))]
    if (topics.length) {
      communityTopicOptions.value = topics.slice(0, 8).map(name => ({ name, description: `汇集社区“${name}”主题中的讨论、实践与可复用经验。` }))
    }
  } catch {
    // 社区主题失败不影响画像与 Skills 主流程。
  }
}

onMounted(() => {
  abilitySpace.prepareProfile()
  skillDomains.forEach(domain => { domain.score = 0 })
  window.addEventListener('keydown', onKeydown)
  void usersApi.getMe().then(user => { displayName.value = user.displayName || '我' }).catch(() => undefined)
  void refreshProfile()
  void loadMySkills()
  void loadCommunityTopics()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.clearTimeout(toastTimer)
})
</script>
