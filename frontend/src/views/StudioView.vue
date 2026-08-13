<template>
  <div class="page-shell studio-page">
    <TopBar />

    <main class="studio-shell content-width">
      <header class="studio-heading">
        <div>
          <RouterLink to="/seniors" class="studio-back"><ChevronLeft :size="14" />Skill 仓库</RouterLink>
          <h1>沉淀我的经验</h1>
          <p>从本人参与的真实社区线程生成私有 Skill 草稿。</p>
        </div>
        <span class="studio-private-note"><LockKeyhole :size="15" />生成结果仅本人可见</span>
      </header>

      <div v-if="loading" class="studio-loading" aria-label="正在读取社区材料">
        <LoaderCircle :size="22" class="spin" /><span>正在读取你的社区材料…</span>
      </div>

      <div v-else-if="loadError" class="studio-load-error" role="alert">
        <CircleAlert :size="22" />
        <span><strong>无法读取经验材料</strong><small>{{ loadError }}</small></span>
        <button type="button" @click="loadMaterials">重新加载</button>
      </div>

      <template v-else-if="materials">
        <section v-if="!materials.llmAvailable" class="studio-model-state" role="status">
          <Cpu :size="20" />
          <span><strong>真实模型尚未配置</strong><small>你仍可查看和选择社区材料；生成私有草稿将在配置真实 LLM 后开放。</small></span>
        </section>

        <div class="studio-core-layout">
          <section class="studio-material-panel" aria-labelledby="studio-material-title">
            <header>
              <div><h2 id="studio-material-title">我的社区材料</h2><p>{{ materials.threads.length }} 个可用线程</p></div>
              <span :class="{ ready: selectedIds.length >= materials.minimumThreads }">{{ selectedIds.length }} / {{ materials.minimumThreads }} 个线程</span>
            </header>

            <div v-if="!materials.threads.length" class="studio-material-empty">
              <MessagesSquare :size="28" />
              <strong>还没有可沉淀的社区材料</strong>
              <p>至少需要 {{ materials.minimumThreads }} 个本人发帖或回复过的独立线程。</p>
              <RouterLink to="/community">前往社区</RouterLink>
            </div>

            <div v-else class="studio-material-list">
              <article v-for="thread in materials.threads" :key="thread.threadId" class="studio-material-item" :class="{ selected: selectedIds.includes(thread.threadId) }">
                <label class="studio-material-select" :for="`material-${thread.threadId}`">
                  <input :id="`material-${thread.threadId}`" v-model="selectedIds" type="checkbox" :value="thread.threadId" />
                  <span class="studio-material-check"><Check :size="13" /></span>
                  <span class="sr-only">选择线程：{{ thread.title }}</span>
                </label>
                <div>
                  <header>
                    <span>{{ thread.domain || '未分类' }}</span>
                    <time :datetime="thread.post.createdAt">{{ formatDate(thread.post.createdAt) }}</time>
                  </header>
                  <h3>{{ thread.title }}</h3>
                  <p>{{ thread.post.body }}</p>
                  <footer>
                    <span><MessageSquareText :size="13" />{{ participationLabel(thread) }}</span>
                    <button type="button" :aria-expanded="expandedThread === thread.threadId" @click.prevent="toggleThread(thread.threadId)">
                      {{ expandedThread === thread.threadId ? '收起材料' : '查看本人内容' }}
                      <ChevronDown :size="13" :class="{ rotated: expandedThread === thread.threadId }" />
                    </button>
                  </footer>
                  <div v-if="expandedThread === thread.threadId" class="studio-owned-comments">
                    <p v-if="!ownedComments(thread).length">该线程由本人发布；生成时会读取完整讨论上下文。</p>
                    <blockquote v-for="comment in ownedComments(thread)" :key="comment.id">{{ comment.body }}</blockquote>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <aside class="studio-draft-panel">
            <header><Sparkles :size="18" /><div><h2>私有 Skill 草稿</h2><p>生成后可查看、下载或定向调用</p></div></header>

            <form class="studio-draft-form" @submit.prevent="generateDraft">
              <label>
                <span>主题</span>
                <input v-model="topic" maxlength="80" placeholder="例如：第一次独立做小型实验" />
              </label>
              <label>
                <span>希望形成的可用方法</span>
                <textarea v-model="goal" maxlength="500" rows="4" placeholder="例如：生成一份开始实验前可逐项检查的流程与停止条件" />
              </label>
              <label>
                <span>归属能力层</span>
                <select v-model="layerId">
                  <option v-for="domain in skillDomains" :key="domain.id" :value="domain.id">{{ domain.name }}</option>
                </select>
              </label>

              <div v-if="generationError" class="studio-generation-error" role="alert">
                <CircleAlert :size="16" />
                <span><strong>{{ generationError.title }}</strong><small>{{ generationError.detail }}</small></span>
              </div>

              <button type="submit" :disabled="!canGenerate">
                <LoaderCircle v-if="generating" :size="16" class="spin" />
                <Sparkles v-else :size="16" />
                {{ generating ? '正在生成私有草稿' : '生成私有 Skill 草稿' }}
              </button>

              <p v-if="materials.llmAvailable && selectedIds.length < materials.minimumThreads" class="studio-requirement">
                还需选择 {{ materials.minimumThreads - selectedIds.length }} 个独立线程
              </p>
            </form>

            <section v-if="draft" class="studio-generated-draft" :style="draftDomainStyle">
              <header>
                <div><span><LockKeyhole :size="12" />私有草稿</span><h3>{{ draft.index.name }}</h3></div>
                <strong>{{ draft.index.version || '草稿' }}</strong>
              </header>
              <p>{{ draft.index.summary || '后端未提供摘要。' }}</p>
              <div v-if="draft.index.tags?.length" class="studio-draft-tags"><span v-for="tag in draft.index.tags" :key="tag">#{{ tag }}</span></div>
              <pre>{{ draft.skillMd }}</pre>
              <footer>
                <button type="button" title="下载完整 Skill 包" aria-label="下载完整 Skill 包" :disabled="downloading" @click="downloadDraft">
                  <LoaderCircle v-if="downloading" :size="16" class="spin" /><Download v-else :size="16" />
                </button>
                <button type="button" @click="openDraft"><ArrowUpRight :size="15" />查看详情</button>
                <button type="button" class="studio-use-draft" @click="useDraft"><MessageCircleQuestion :size="15" />定向调用</button>
              </footer>
            </section>
          </aside>
        </div>
      </template>
    </main>

    <Transition name="fade"><div v-if="toast" class="toast" role="status">{{ toast }}</div></Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowUpRight,
  Check,
  ChevronDown,
  ChevronLeft,
  CircleAlert,
  Cpu,
  Download,
  LoaderCircle,
  LockKeyhole,
  MessageCircleQuestion,
  MessagesSquare,
  MessageSquareText,
  Sparkles,
} from '@lucide/vue'
import { domainById, domainForLabel, skillDomains } from '../domain'
import { distillOwnSkill, downloadSkillBundle, fetchOwnMaterials, SkillApiError } from '../services/seniorService'
import type { OwnMaterialThread, OwnMaterialsResult, SeniorSkillDetail } from '../skillsProfileTypes'
import TopBar from '../components/skillslab/SkillsProfileTopBar.vue'

const route = useRoute()
const router = useRouter()
const materials = ref<OwnMaterialsResult>()
const loading = ref(true)
const loadError = ref('')
const selectedIds = ref<string[]>([])
const expandedThread = ref('')
const topic = ref(typeof route.query.source === 'string' ? route.query.source : '')
const goal = ref('')
const initialDomain = typeof route.query.domain === 'string' ? route.query.domain : ''
const layerId = ref(domainForLabel(`${initialDomain} ${topic.value}`).id)
const generating = ref(false)
const generationError = ref<{ title: string; detail: string }>()
const draft = ref<SeniorSkillDetail>()
const downloading = ref(false)
const toast = ref('')

const canGenerate = computed(() => Boolean(
  materials.value?.llmAvailable
  && selectedIds.value.length >= (materials.value?.minimumThreads ?? 3)
  && topic.value.trim()
  && goal.value.trim()
  && !generating.value,
))
const draftDomainStyle = computed(() => {
  const domain = domainById(draft.value?.index.layerId || layerId.value)
  return { '--domain-color': domain.color, '--domain-ink': domain.ink, '--domain-tint': domain.tint }
})

onMounted(loadMaterials)

async function loadMaterials() {
  loading.value = true
  loadError.value = ''
  try {
    materials.value = await fetchOwnMaterials()
  } catch (cause) {
    materials.value = undefined
    loadError.value = cause instanceof Error ? cause.message : '无法读取社区经验材料'
  } finally {
    loading.value = false
  }
}

function toggleThread(id: string) {
  expandedThread.value = expandedThread.value === id ? '' : id
}

function ownedComments(thread: OwnMaterialThread) {
  const owned = new Set(thread.ownedCommentIds)
  return thread.comments.filter(comment => owned.has(comment.id))
}

function participationLabel(thread: OwnMaterialThread) {
  const count = ownedComments(thread).length
  return count ? `${count} 条本人回复` : '本人发布的主题'
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}

async function generateDraft() {
  if (!canGenerate.value) return
  generating.value = true
  generationError.value = undefined
  draft.value = undefined
  try {
    draft.value = await distillOwnSkill({
      topic: topic.value.trim(),
      goal: goal.value.trim(),
      threadIds: selectedIds.value,
      layerId: layerId.value,
    })
    showToast('私有 Skill 草稿已生成')
  } catch (cause) {
    generationError.value = generationMessage(cause)
  } finally {
    generating.value = false
  }
}

function generationMessage(cause: unknown) {
  if (!(cause instanceof SkillApiError)) return { title: '草稿生成失败', detail: '请稍后重试；本次不会保留半成品。' }
  if (cause.code === 'DISTILL_LLM_UNAVAILABLE') return { title: '真实模型尚未配置', detail: '材料未提交到 mock，当前不会生成占位草稿。' }
  if (cause.code === 'DISTILL_INSUFFICIENT_EVIDENCE') {
    const details = cause.details as {
      minimumThreads?: number; selectedThreads?: number; missingThreads?: number;
      violations?: string[]; missingEvidence?: string[]; openQuestions?: string[];
    } | undefined
    const missing = details?.missingThreads ?? 0
    if (missing > 0) {
      const minimum = details?.minimumThreads ?? materials.value?.minimumThreads ?? 3
      const selected = details?.selectedThreads ?? selectedIds.value.length
      return { title: '独立线程证据不足', detail: `沉淀一个 Skill 至少需要 ${minimum} 个本人发帖或回复过的独立线程，当前有效 ${selected} 个，还缺 ${missing} 个。` }
    }
    const violations = details?.violations ?? []
    const missingEvidence = details?.missingEvidence ?? []
    const reason = evidenceInsufficientReason(violations)
    const suggestions = missingEvidence.map(item => `· ${item}`)
    const detail = [reason, ...suggestions].filter(Boolean).join('\n')
      + (suggestions.length ? '\n建议优先选择本人深度参与、发言较长的线程，再重新生成。' : '')
    return { title: '材料证据不足', detail }
  }
  if (cause.code === 'DISTILL_GENERATION_FAILED') return { title: '草稿生成失败', detail: `${cause.message}。本次不会保留半成品。` }
  return { title: '草稿生成失败', detail: cause.message || '请稍后重试；本次不会保留半成品。' }
}

function evidenceInsufficientReason(violations: string[]): string {
  const all = violations.join('\n')
  if (all.includes('未知证据')) return '生成的草稿引用了不存在的证据编号，请重新生成试试。'
  if (all.includes('独立 thread')) return '草稿的核心规则只覆盖了部分独立线程，需要覆盖 3 个以上。'
  if (all.includes('本人证据正文过短')) return '部分被引用的本人发言太短（不足 8 个字），无法支撑证据链。'
  if (all.includes('message_id 不属于')) return '草稿引用了不属于本人的消息作为证据。'
  if (all.includes('mode 必须') || all.includes('maturity.decision')) return '模型判断当前材料不足以生成完整 Skill，只能沉淀为经验片段。'
  if (all.includes('成熟度')) return '生成结果未达到成熟度要求（总分不低于 12、各项不低于 2）。'
  if (all.includes('workflow') || all.includes('boundaries') || all.includes('decision_points')) return '草稿缺少必要章节（执行流程、能力边界或决策节点）。'
  return violations[0] || '所选材料暂时不足以生成完整 Skill，请补充更多本人参与的高质量讨论后再试。'
}

async function downloadDraft() {
  if (!draft.value || downloading.value) return
  downloading.value = true
  try {
    await downloadSkillBundle(draft.value.index.id, draft.value.index.name)
    showToast('私有 Skill 七件套已下载')
  } catch (cause) {
    showToast(cause instanceof Error ? cause.message : '下载失败，请稍后重试')
  } finally {
    downloading.value = false
  }
}

function openDraft() {
  if (draft.value) void router.push(`/seniors/${draft.value.index.id}`)
}

function useDraft() {
  if (!draft.value) return
  const task = goal.value.trim() || topic.value.trim()
  sessionStorage.setItem('skillslab:question-draft', task)
  sessionStorage.setItem('skillslab:task-context', task)
  sessionStorage.setItem('skillslab:senior-id', draft.value.index.id)
  void router.push('/')
}

let toastTimer = 0
function showToast(message: string) {
  toast.value = message
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => { toast.value = '' }, 3200)
}
</script>
