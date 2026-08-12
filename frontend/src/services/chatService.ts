import axios from 'axios'
import type { ChatResponse } from '../types'

const BASE = import.meta.env.VITE_API_BASE ?? ''

export async function askSeniors(message: string, sessionId?: string): Promise<ChatResponse> {
  const res = await axios.post<ChatResponse>(`${BASE}/api/chat`, {
    message,
    sessionId,
  }, { timeout: 60000 })
  return res.data
}
