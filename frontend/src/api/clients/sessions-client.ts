import { httpJson } from '../http'

export type SessionState = 'ACTIVE' | 'FORCED_BREAK' | 'ENDED'

export type BreakPolicy = {
  breakAfterMin: number
  breakDurationMin: number
}

export type SessionStartResponse = {
  sessionId: string
  state: SessionState
  hardStopAt: string
  resumeAt?: string
  breakPolicy: BreakPolicy
  content: {
    contentId: string
    title: string
  }
}

export type SessionHeartbeatRequest = {
  elapsedSec: number
  active: boolean
}

export type SessionHeartbeatResponse = {
  state: SessionState
  resumeAt?: string
}

export async function startSession(childId: string, input: { planItemId: string; clientTime: string }) {
  return await httpJson<SessionStartResponse>(`/children/${childId}/sessions/start`, {
    method: 'POST',
    body: input,
  })
}

export async function heartbeat(sessionId: string, input: SessionHeartbeatRequest) {
  return await httpJson<SessionHeartbeatResponse>(`/sessions/${sessionId}/heartbeat`, {
    method: 'POST',
    body: input,
  })
}

export async function postEvent(sessionId: string, input: { type: string; meta?: Record<string, unknown> }) {
  return await httpJson<void>(`/sessions/${sessionId}/events`, {
    method: 'POST',
    body: input,
  })
}

export async function endSession(sessionId: string, input: { reason: string }) {
  return await httpJson<void>(`/sessions/${sessionId}/end`, {
    method: 'POST',
    body: input,
  })
}
