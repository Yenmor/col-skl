const ANIME_COUNT = 240
const MISC_COUNT = 48
const TOTAL = ANIME_COUNT + MISC_COUNT
const ANIME_SHARE = 0.7
const ANIME_CUTOFF = Math.floor(TOTAL * ANIME_SHARE)

const ANIME_PNG_START = 121

function fnv1a(str: string): number {
  let h = 0x811c9dc5
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

export function avatarFor(id: string | null | undefined): string {
  const seed = id && id.trim() ? id : 'anonymous'
  const idx = fnv1a(seed) % TOTAL
  if (idx < ANIME_CUTOFF) {
    const a = idx % ANIME_COUNT
    const ext = a < ANIME_PNG_START ? 'jpg' : 'png'
    return `/avatars/anime/a${String(a + 1).padStart(3, '0')}.${ext}`
  }
  const m = (idx - ANIME_CUTOFF) % MISC_COUNT
  return `/avatars/misc/m${String(m + 1).padStart(3, '0')}.png`
}
