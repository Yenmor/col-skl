<template>
  <div class="page-shell detail-page">
    <TopBar />

    <div v-if="loading" class="detail-skeleton content-width" aria-label="正在加载 Skill 详情"><span /><span /><span /></div>
    <div v-else-if="errorMessage" class="empty-state content-width" role="alert">
      <LockKeyhole v-if="errorCode === 'SKILL_FORBIDDEN'" :size="32" />
      <SearchX v-else :size="32" />
      <h2>{{ errorCode === 'SKILL_FORBIDDEN' ? '这份 Skill 是私有草稿' : '无法读取 Skill 详情' }}</h2>
      <p>{{ errorMessage }}</p>
      <RouterLink to="/seniors">返回 Skill 仓库</RouterLink>
    </div>

    <main v-else-if="detail && meta" class="skill-detail-shell content-width" :style="domainStyle">
      <button class="back-link" type="button" @click="router.back()"><ArrowLeft :size="16" />返回</button>

      <section class="skill-fit-hero">
        <div class="skill-fit-intro">
          <div class="detail-title-line">
            <span class="domain-tag">{{ meta.domain || domain.name }}</span>
            <span v-if="meta.visibility === 'PRIVATE'" class="visibility-tag"><LockKeyhole :size="11" />私有草稿</span>
            <small>{{ meta.version || '版本未标注' }}</small>
          </div>
          <h1>{{ meta.name }}</h1>
          <p class="detail-summary">{{ meta.summary || '后端尚未提供这份 Skill 的摘要。' }}</p>

          <form class="detail-task-form" @submit.prevent="matchTask">
            <label class="detail-task-field">
              <span><Target :size="16" />当前任务</span>
              <input v-model="currentTask" maxlength="500" placeholder="写下要用这份 Skill 完成的具体任务" @input="rememberTask" />
            </label>
            <button type="submit" :disabled="matching || !currentTask.trim()">
              <LoaderCircle v-if="matching" :size="16" class="spin" />
              <SearchCheck v-else :size="16" />
              {{ matching ? '正在匹配' : '检查匹配度' }}
            </button>
          </form>

          <div v-if="matchedTask" class="detail-match-result" :class="{ empty: !taskMatch }">
            <template v-if="taskMatch">
              <span>{{ recallMatchLabel(taskMatch) }}</span>
              <strong>{{ Math.round(taskMatch.score * 100) }}<small>%</small></strong>
              <p>{{ taskMatch.text || '召回服务认为这份 Skill 与当前任务相关。' }}</p>
            </template>
            <template v-else>
              <CircleHelp :size="17" />
              <p>真实召回结果中没有这份 Skill。仍可定向调用，但需要自行核对适用场景。</p>
            </template>
          </div>
          <p v-else-if="matchError" class="detail-inline-error"><CircleAlert :size="14" />{{ matchError }}</p>

          <div class="detail-primary-actions">
            <button type="button" @click="useSkill"><MessageCircleQuestion :size="16" />带着当前任务调用</button>
            <button type="button" title="下载完整 Skill 包" aria-label="下载完整 Skill 包" :disabled="downloading" @click="downloadSkill">
              <LoaderCircle v-if="downloading" :size="17" class="spin" />
              <Download v-else :size="17" />
            </button>
          </div>
        </div>

        <div class="skill-fit-explainer">
          <header>
            <div><h2>Skill 包质量</h2><p>后端解析七件套、方法与适用边界；不混入任务匹配度，也不判断经历真伪。</p></div>
            <Info :size="17" />
          </header>
          <div v-if="trust" class="fit-visual-row">
            <SkillFitPolygon :scores="trust.scores" :overall="trust.overall" :level="trust.level" />
            <ol class="fit-axis-list">
              <li v-for="axis in trustAxes" :key="axis.label">
                <span><i :style="{ width: `${axis.score}%` }" /></span>
                <div><b>{{ axis.label }}</b><small>{{ axis.note }}</small></div>
                <strong>{{ axis.score }}</strong>
              </li>
            </ol>
          </div>
          <div v-else class="detail-trust-empty">
            <CircleHelp :size="24" />
            <span><strong>后端未返回包质量指标</strong><small>页面不会根据摘要、学校或来源类型自行猜分。</small></span>
          </div>
        </div>
      </section>

      <section class="trust-evidence-panel" aria-labelledby="trust-evidence-title">
        <header>
          <div>
            <h2 id="trust-evidence-title">信任证据</h2>
            <p>{{ trustEvidenceSummary }}</p>
          </div>
          <span v-if="trustEvidence" class="trust-evidence-level">
            <strong>{{ scoreLabel(trustEvidence.overall) }}</strong>
            <small>{{ trustEvidence.level || '综合信任待补充' }}</small>
          </span>
        </header>

        <div class="trust-evidence-grid">
          <article v-for="item in trustEvidenceItems" :key="item.key" :class="`is-${item.tone}`">
            <span class="trust-evidence-icon"><component :is="item.icon" :size="17" /></span>
            <div>
              <header><h3>{{ item.title }}</h3><strong>{{ item.label }}</strong></header>
              <p>{{ item.detail }}</p>
              <dl v-if="item.metrics.length">
                <div v-for="metric in item.metrics" :key="metric.label"><dt>{{ metric.label }}</dt><dd>{{ metric.value }}</dd></div>
              </dl>
            </div>
          </article>
        </div>

        <p class="trust-evidence-note"><ShieldAlert :size="14" />AI 只能辅助检查结构、来源映射与文本一致性；平台核验不是权威认证，也不能替代你对适用场景的判断。</p>
      </section>

      <section class="skill-trust-strip">
        <div class="detail-avatar senior-avatar"><img v-if="avatarSrc" :src="avatarSrc" :alt="meta.name" @error="avatarFailed = true" /><span v-else>{{ meta.name.slice(0, 1) }}</span></div>
        <div class="skill-contributor">
          <small>经验贡献者</small>
          <strong>{{ contributor }}</strong>
          <span>{{ meta.year ? `${meta.year} 届 · ` : '' }}{{ sourceLabel }}</span>
        </div>
        <dl>
          <div><dt>版本</dt><dd>{{ meta.version || '未标注' }}</dd></div>
          <div><dt>来源映射</dt><dd>{{ sourceMappingLabel }}</dd></div>
          <div><dt>更新</dt><dd>{{ formattedDate }}</dd></div>
        </dl>
      </section>

      <article class="skill-document">
        <header>
          <div><h2>方法与依据</h2><p>正文和结构来自服务器中的真实 Skill 包。</p></div>
          <span>{{ meta.visibility === 'PRIVATE' ? '私有草稿' : '公开 Skill' }}</span>
        </header>
        <nav class="skill-detail-tabs" aria-label="Skill 详情内容">
          <button v-for="tab in tabs" :key="tab.id" type="button" :class="{ active: activeTab === tab.id }" @click="activeTab = tab.id">{{ tab.label }}</button>
        </nav>

        <section v-if="activeTab === 'method'" class="skill-section">
          <div v-if="detail.skillMd" class="skill-body">{{ detail.skillMd }}</div>
          <div v-else class="fragment-empty"><FileWarning :size="18" /><span><strong>SKILL.md 为空</strong><small>请检查服务器上的 Skill 包。</small></span></div>
        </section>

        <template v-else-if="activeTab === 'source'">
          <section class="source-summary" :class="{ unavailable: !detail.sources.available }">
            <DatabaseZap :size="19" />
            <div v-if="detail.sources.available">
              <strong>{{ detail.sources.threadCount }} 个线程 · {{ detail.sources.mappingCount }} 条来源映射</strong>
              <p>{{ sourceVerificationLabel }}。证据 ID：{{ detail.sources.evidenceIds.length ? detail.sources.evidenceIds.join('、') : '未提供' }}</p>
            </div>
            <div v-else>
              <strong>没有可用的来源映射</strong>
              <p>{{ detail.sources.missingReason || '该 Skill 包尚未提供 sources 映射。' }}</p>
            </div>
          </section>
          <section v-if="detail.workMd" class="skill-section"><h3>实践材料</h3><div class="skill-body">{{ detail.workMd }}</div></section>
          <section v-if="detail.personaMd" class="skill-section"><h3>表达与判断材料</h3><div class="skill-body">{{ detail.personaMd }}</div></section>
          <div v-if="!detail.workMd && !detail.personaMd" class="fragment-empty"><FileWarning :size="18" /><span><strong>未提供可展示的原始材料</strong><small>来源摘要仍以服务器返回结果为准。</small></span></div>
        </template>

        <template v-else-if="activeTab === 'fragments'">
          <section class="skill-fragments">
            <header><div><h3>已沉淀的经验片段</h3><p>来自主干片段接口，保留内容类型、时间和标签，供方法与来源复核。</p></div><strong>{{ fragments.length }}<small>条片段</small></strong></header>
            <div v-if="fragmentsLoading" class="fragment-empty">正在读取经验片段…</div>
            <div v-else-if="fragments.length" class="fragment-list">
              <article v-for="fragment in fragments" :key="fragment.id">
                <header><span :class="`fragment-kind kind-${fragment.kind.toLowerCase()}`">{{ fragmentKindLabel(fragment.kind) }}</span><time :datetime="fragment.createdAt">{{ relativeTime(fragment.createdAt) }}</time></header>
                <p>{{ fragment.content }}</p>
                <footer v-if="fragment.tags.length"><span v-for="tag in fragment.tags" :key="tag">#{{ tag }}</span></footer>
              </article>
            </div>
            <div v-else class="fragment-empty"><CircleHelp :size="18" /><span><strong>还没有可展示的经验片段</strong><small>后端不可用或该 Skill 尚未完成片段沉淀。</small></span></div>
          </section>
        </template>

        <template v-else>
          <section class="skill-section"><h3>manifest.json</h3><pre class="skill-json">{{ JSON.stringify(detail.manifest, null, 2) }}</pre></section>
          <section class="skill-section"><h3>meta.json</h3><pre class="skill-json">{{ JSON.stringify(detail.meta, null, 2) }}</pre></section>
        </template>
      </article>

      <section class="skill-growth-panel" aria-labelledby="skill-growth-title">
        <header>
          <div><h2 id="skill-growth-title">成长建议</h2><p>使用者可以交流体验，也可以提出会进入下一版本候选的具体修改。</p></div>
          <span>演示闭环 · 仅保存在本机</span>
        </header>

        <div class="skill-growth-tabs" role="tablist" aria-label="成长建议类型">
          <button v-for="tab in growthTabs" :key="tab.id" type="button" role="tab" :aria-selected="growthKind === tab.id" :class="{ active: growthKind === tab.id }" @click="growthKind = tab.id">
            {{ tab.label }} <small>{{ growthCount(tab.id) }}</small>
          </button>
        </div>

        <form class="skill-growth-composer" @submit.prevent="submitGrowthFeedback">
          <textarea v-model="growthDraft" maxlength="600" rows="3" :placeholder="growthKind === 'COMMENT' ? '说说你实际使用后的感受或补充…' : '指出具体要改的位置、原因和建议改法…'" />
          <footer><small>{{ growthKind === 'COMMENT' ? '评论用于交流，不直接改变 Skill。' : '修改建议可分别被作者和平台采纳。' }}</small><button type="submit" :disabled="!growthDraft.trim()"><Send :size="15" />提交{{ growthKind === 'COMMENT' ? '评论' : '建议' }}</button></footer>
        </form>

        <div class="skill-growth-list">
          <article v-for="item in visibleGrowthFeedback" :key="item.id">
            <span class="skill-growth-avatar">{{ item.authorName.slice(0, 1) }}</span>
            <div>
              <header><strong>{{ item.authorName }}</strong><time :datetime="item.createdAt">{{ growthDate(item.createdAt) }}</time><span>演示</span></header>
              <p>{{ item.body }}</p>
              <footer v-if="item.kind === 'SUGGESTION'">
                <button type="button" :class="{ adopted: item.authorDecision === 'ADOPTED' }" :disabled="item.authorDecision === 'ADOPTED'" @click="adoptGrowth(item.id, 'author')"><UserRoundCheck :size="14" />{{ item.authorDecision === 'ADOPTED' ? '作者已采纳' : '作者采纳' }}</button>
                <button type="button" :class="{ adopted: item.platformDecision === 'ADOPTED' }" :disabled="item.platformDecision === 'ADOPTED'" @click="adoptGrowth(item.id, 'platform')"><BadgeCheck :size="14" />{{ item.platformDecision === 'ADOPTED' ? '平台已采纳' : '平台采纳' }}</button>
                <small>采纳后进入版本待办，不代表内容已自动改写。</small>
              </footer>
            </div>
          </article>
        </div>
      </section>
    </main>

    <Transition name="fade"><div v-if="toast" class="toast" role="status">{{ toast }}</div></Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  BadgeCheck,
  CircleAlert,
  CircleHelp,
  DatabaseZap,
  Download,
  FileWarning,
  Fingerprint,
  Info,
  LoaderCircle,
  LockKeyhole,
  MessageCircleQuestion,
  MessagesSquare,
  SearchCheck,
  SearchX,
  Send,
  ShieldAlert,
  ShieldCheck,
  Target,
  UserRoundCheck,
} from '@lucide/vue'
import { domainForLabel, skillDomains } from '../domain'
import { fitAxisLabels, recallMatchLabel, skillTrustFor } from '../skillFit'
import { adoptSkillGrowthFeedback, avatarUrl, downloadSkillBundle, fetchSenior, loadSkillGrowthFeedback, recallSkills, SkillApiError, submitSkillGrowthFeedback } from '../services/seniorService'
import { seniorsApi } from '../services/api-v1'
import type { SeniorFragmentDto } from '../types/api-v1'
import type { SeniorSkillDetail, SkillGrowthFeedback, SkillGrowthKind, SkillRecallMatch, TrustEvidenceItem } from '../skillsProfileTypes'
import SkillFitPolygon from '../components/skillslab/SkillFitPolygon.vue'
import TopBar from '../components/skillslab/SkillsProfileTopBar.vue'

const route = useRoute()
const router = useRouter()
const detail = ref<SeniorSkillDetail>()
const loading = ref(true)
const errorMessage = ref('')
const errorCode = ref('')
const currentTask = ref(sessionStorage.getItem('skillslab:task-context') || '')
const matching = ref(false)
const matchedTask = ref('')
const matchError = ref('')
const taskMatch = ref<SkillRecallMatch>()
const downloading = ref(false)
const avatarFailed = ref(false)
const toast = ref('')
const activeTab = ref<'method' | 'source' | 'package' | 'fragments'>('method')
const growthKind = ref<SkillGrowthKind>('COMMENT')
const growthDraft = ref('')
const growthFeedback = ref<SkillGrowthFeedback[]>([])
const fragments = ref<SeniorFragmentDto[]>([])
const fragmentsLoading = ref(true)
const growthTabs = [{ id: 'COMMENT' as const, label: '评论' }, { id: 'SUGGESTION' as const, label: '修改建议' }]
const tabs = [
  { id: 'method' as const, label: '方法正文' },
  { id: 'source' as const, label: '来源摘要' },
  { id: 'fragments' as const, label: '经验片段' },
  { id: 'package' as const, label: '包元数据' },
]
const axisNotes = ['真实校园情境在材料中的覆盖程度', '结论与线程、证据 ID 的映射程度', '流程、输入、输出和检查点的完整度', '不适用情境与停止条件的完整度', '必需文件与 JSON 结构的完整度']

const meta = computed(() => detail.value?.index)
const domain = computed(() => skillDomains.find(item => item.id === meta.value?.layerId)
  ?? domainForLabel(`${meta.value?.domain ?? ''} ${meta.value?.name ?? ''}`))
const domainStyle = computed(() => ({ '--domain-color': domain.value.color, '--domain-ink': domain.value.ink, '--domain-tint': domain.value.tint }))
const avatarSrc = computed(() => meta.value && !avatarFailed.value ? avatarUrl(meta.value) : '')
const trust = computed(() => meta.value ? skillTrustFor(meta.value) : undefined)
const trustAxes = computed(() => trust.value ? fitAxisLabels.map((label, index) => ({ label, score: trust.value!.scores[index], note: axisNotes[index] })) : [])
const trustEvidence = computed(() => detail.value?.trustEvidence)
const trustEvidenceSummary = computed(() => trustEvidence.value?.summary
  || '将包质量之外的可信依据拆开呈现。缺失的数据会明确标为暂无，不由前端推算。')
const trustEvidenceItems = computed(() => [
  evidenceView('source', '来源本人确认 / 授权', Fingerprint, trustEvidence.value?.source, [
    { label: '证据分', value: scoreLabel(trustEvidence.value?.source?.score) },
    { label: '确认', value: truthLabel(trustEvidence.value?.source?.confirmed) },
    { label: '授权', value: truthLabel(trustEvidence.value?.source?.authorized) },
  ]),
  evidenceView('platform', '权威渠道 / 平台核验', ShieldCheck, trustEvidence.value?.platform, [
    { label: '证据分', value: scoreLabel(trustEvidence.value?.platform?.score) },
    { label: 'AI 辅助分', value: scoreLabel(trustEvidence.value?.platform?.aiScore) },
    { label: '权威渠道', value: countLabel(trustEvidence.value?.platform?.authorityCount) },
  ]),
  evidenceView('community', '社区采用', MessagesSquare, trustEvidence.value?.community, [
    { label: '证据分', value: scoreLabel(trustEvidence.value?.community?.score) },
    { label: '赞', value: countLabel(trustEvidence.value?.community?.likes) },
    { label: '下载', value: countLabel(trustEvidence.value?.community?.downloads) },
    { label: '评论', value: countLabel(trustEvidence.value?.community?.comments) },
  ]),
])
const contributor = computed(() => [meta.value?.school, meta.value?.major].filter(Boolean).join(' · ') || '未提供')
const sourceLabel = computed(() => meta.value?.source === 'distilled' ? '本人经验沉淀' : meta.value?.source ? '贡献者上传' : '来源类型未标注')
const sourceMappingLabel = computed(() => detail.value?.sources.available ? `${detail.value.sources.mappingCount} 条` : '缺失')
const sourceVerificationLabel = computed(() => detail.value?.sources.verification === 'PLATFORM_VERIFIED'
  ? '平台沉淀流程已校验映射结构与本人消息归属'
  : '仅确认 Skill 包内部声明和映射结构一致，未核验原始经历真伪')
const formattedDate = computed(() => {
  const value = meta.value?.updatedAt || meta.value?.createdAt
  if (!value) return '未提供'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
})
const visibleGrowthFeedback = computed(() => growthFeedback.value.filter(item => item.kind === growthKind.value))

function growthCount(kind: SkillGrowthKind) {
  return growthFeedback.value.filter(item => item.kind === kind).length
}

function growthDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function evidenceView(key: string, title: string, icon: typeof Fingerprint, evidence: TrustEvidenceItem | null | undefined, metrics: Array<{ label: string; value: string }>) {
  const status = evidence?.status?.toUpperCase() || 'MISSING'
  const tone = status === 'CONFIRMED' || status === 'VERIFIED' || status === 'AVAILABLE'
    ? 'confirmed'
    : status === 'PARTIAL' || status === 'DECLARED'
      ? 'partial'
      : 'missing'
  return {
    key,
    title,
    icon,
    tone,
    label: evidence?.label || (status === 'NOT_TRACKED' ? '尚未统计' : '暂无证据'),
    detail: evidence?.detail || '后端尚未提供这组真实数据。',
    metrics,
  }
}

function truthLabel(value: boolean | undefined) {
  return value === true ? '是' : value === false ? '否' : '暂无'
}

function countLabel(value: number | null | undefined) {
  return typeof value === 'number' ? String(value) : '暂无'
}

function scoreLabel(value: number | null | undefined) {
  return typeof value === 'number' ? `${value} / 100` : '暂无'
}

async function loadDetail(id: string) {
  loading.value = true
  avatarFailed.value = false
  errorMessage.value = ''
  errorCode.value = ''
  activeTab.value = 'method'
  fragmentsLoading.value = true
  fragments.value = []
  try {
    detail.value = await fetchSenior(id)
    growthFeedback.value = loadSkillGrowthFeedback(id)
  } catch (cause) {
    detail.value = undefined
    errorCode.value = cause instanceof SkillApiError ? cause.code : ''
    errorMessage.value = cause instanceof Error ? cause.message : '无法读取这份 Skill'
  } finally {
    loading.value = false
  }
  const fragmentResult = await Promise.allSettled([seniorsApi.listFragments(id, { limit: 50 })])
  fragments.value = fragmentResult[0].status === 'fulfilled' ? fragmentResult[0].value : []
  fragmentsLoading.value = false
}

function fragmentKindLabel(kind: SeniorFragmentDto['kind']) {
  return ({ PERSONA: '表达特征', WORK: '实践经历', MEMORY: '经验记忆', OTHER: '其他' } as const)[kind] ?? '其他'
}

function relativeTime(iso: string) {
  const elapsed = Date.now() - new Date(iso).getTime()
  if (elapsed < 60_000) return '刚刚'
  if (elapsed < 3_600_000) return `${Math.floor(elapsed / 60_000)} 分钟前`
  if (elapsed < 86_400_000) return `${Math.floor(elapsed / 3_600_000)} 小时前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

function submitGrowthFeedback() {
  if (!meta.value || !growthDraft.value.trim()) return
  growthFeedback.value = submitSkillGrowthFeedback(meta.value.id, growthKind.value, growthDraft.value)
  growthDraft.value = ''
  showToast(growthKind.value === 'COMMENT' ? '演示评论已保存在本机' : '演示建议已进入待处理列表')
}

function adoptGrowth(id: string, actor: 'author' | 'platform') {
  if (!meta.value) return
  growthFeedback.value = adoptSkillGrowthFeedback(meta.value.id, id, actor)
  showToast(actor === 'author' ? '作者采纳状态已更新（演示）' : '平台采纳状态已更新（演示）')
}

onMounted(() => loadDetail(String(route.params.id)))
watch(() => route.params.id, id => loadDetail(String(id)))

function rememberTask() {
  sessionStorage.setItem('skillslab:task-context', currentTask.value.trim())
}

async function matchTask() {
  if (!meta.value || !currentTask.value.trim()) return
  rememberTask()
  matching.value = true
  matchedTask.value = ''
  matchError.value = ''
  taskMatch.value = undefined
  try {
    const matches = await recallSkills(currentTask.value.trim(), 20)
    taskMatch.value = matches.get(meta.value.id)
    matchedTask.value = currentTask.value.trim()
  } catch (cause) {
    matchError.value = cause instanceof Error ? cause.message : '任务匹配暂时不可用'
  } finally {
    matching.value = false
  }
}

function useSkill() {
  if (!meta.value) return
  const task = currentTask.value.trim()
  if (!task) {
    showToast('先写下具体任务，再调用这份 Skill')
    return
  }
  rememberTask()
  sessionStorage.setItem('skillslab:question-draft', task)
  sessionStorage.setItem('skillslab:senior-id', meta.value.id)
  void router.push('/')
}

async function downloadSkill() {
  if (!meta.value || downloading.value) return
  downloading.value = true
  try {
    await downloadSkillBundle(meta.value.id, meta.value.name)
    showToast('完整七件套已下载')
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '下载失败，请稍后重试')
  } finally {
    downloading.value = false
  }
}

let toastTimer = 0
function showToast(message: string) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 3200)
}
</script>
