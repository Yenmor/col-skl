<template>
  <form
    class="composer"
    :class="{ 'composer-compact': compact }"
    @submit.prevent="submit"
  >
    <div class="composer-inner">
      <span
        v-if="context"
        class="composer-context"
        :style="contextColor ? { color: contextColor } : undefined"
      >
        <i :style="contextColor ? { background: contextColor } : undefined" />{{ context }}
      </span>

      <textarea
        v-model="text"
        rows="1"
        :disabled="loading"
        :placeholder="placeholder"
        @keydown.enter.exact.prevent="submit"
      />

      <div class="composer-compact-footer">
        <div class="composer-compact-tools">
          <button
            type="button"
            class="composer-skill-trigger"
            :aria-expanded="menuOpen"
            aria-label="按方向选择学长"
            @click="menuOpen = !menuOpen"
          >
            <span class="trigger-glyph">▤</span>按方向选择学长
            <ChevronDown :size="12" :class="{ rotated: menuOpen }" />
          </button>
        </div>
        <button class="composer-submit" type="submit" :disabled="loading || !text.trim()" aria-label="发送">
          {{ loading ? '…' : '↑' }}
        </button>
      </div>
    </div>

    <Transition name="composer-skills">
      <div v-if="menuOpen" class="composer-skill-menu" aria-label="方向列表">
        <button
          v-for="domain in skillDomains"
          :key="domain.id"
          type="button"
          @click="pickDomain(domain.id)"
        >
          <span :style="{ background: domain.color, color: domain.ink }">{{ domain.glyph }}</span>
          <strong>{{ domain.name }}</strong>
          <small>{{ domain.code }} / {{ domain.aliases.join(' · ') }}</small>
        </button>
      </div>
    </Transition>
  </form>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { skillDomains, type DomainId } from '../../domain'

const props = defineProps<{
  loading?: boolean
  placeholder?: string
  compact?: boolean
  context?: string
  contextColor?: string
}>()

const emit = defineEmits<{
  (e: 'submit', text: string): void
  (e: 'select-domain', id: DomainId): void
}>()

const text = ref('')
const menuOpen = ref(false)

function submit() {
  if (!text.value.trim() || props.loading) return
  emit('submit', text.value.trim())
  text.value = ''
}

function pickDomain(id: DomainId) {
  menuOpen.value = false
  emit('select-domain', id)
}
</script>

<style scoped>
/* 仅补 guan style.css 未定义的细节；视觉主体全部来自全局 guan 样式 */
.trigger-glyph {
  width: 15px;
  height: 15px;
  display: grid;
  place-items: center;
  border-radius: 3px;
  background: var(--ink);
  color: white;
  font-size: 8px;
  font-weight: 900;
}
</style>
