import { computed, onBeforeUnmount, ref } from 'vue'
import type { SessionState, SessionStartResponse } from '../../api/clients/sessions-client'
import * as sessionsClient from '../../api/clients/sessions-client'
import { useSessionStore } from './store'

type MachineView = {
  state: SessionState
  hardStopAt: Date
  resumeAt?: Date
  now: Date
  secondsToHardStop: number
  secondsToResume: number | null
  contentTitle: string
}

function clampNonNegative(n: number) {
  return Math.max(0, Math.floor(n))
}

export function useSessionMachine(input: { childId: string; planItemId: string }) {
  const sessionStore = useSessionStore()

  const busy = ref(false)
  const error = ref<string | null>(null)

  const sessionId = ref<string | null>(null)
  const contentTitle = ref<string>('')
  const state = ref<SessionState>('ENDED')

  const hardStopAt = ref<Date | null>(null)
  const resumeAt = ref<Date | null>(null)
  const startedAtClient = ref<Date | null>(null)

  const tickNow = ref(new Date())
  const elapsedSec = computed(() => {
    if (!startedAtClient.value) return 0
    return clampNonNegative((tickNow.value.getTime() - startedAtClient.value.getTime()) / 1000)
  })

  let tickTimer: number | null = null
  let heartbeatTimer: number | null = null

  function startTicking() {
    stopTicking()
    tickTimer = window.setInterval(() => {
      tickNow.value = new Date()

      if (state.value !== 'ENDED' && hardStopAt.value) {
        const remaining = (hardStopAt.value.getTime() - tickNow.value.getTime()) / 1000
        if (remaining <= 0) {
          void end('TIME_LIMIT')
        }
      }
    }, 1000)
  }

  function stopTicking() {
    if (tickTimer) {
      window.clearInterval(tickTimer)
      tickTimer = null
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = window.setInterval(async () => {
      if (!sessionId.value) return
      if (state.value === 'ENDED') return

      try {
        const res = await sessionsClient.heartbeat(sessionId.value, {
          elapsedSec: elapsedSec.value,
          active: state.value === 'ACTIVE',
        })

        if (res.state && res.state !== state.value) {
          state.value = res.state
          if (res.resumeAt) resumeAt.value = new Date(res.resumeAt)
          sessionStore.setCurrentSession({
            sessionId: sessionId.value,
            childId: input.childId,
            planItemId: input.planItemId,
            state: state.value,
            hardStopAt: hardStopAt.value?.toISOString() ?? new Date().toISOString(),
            resumeAt: resumeAt.value?.toISOString(),
            startedAtClient: startedAtClient.value?.toISOString() ?? new Date().toISOString(),
          })
        }
      } catch {
        // offline / transient error: keep local state; server is authoritative when reachable
      }
    }, 30000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  async function start() {
    busy.value = true
    error.value = null

    try {
      const clientTime = new Date().toISOString()
      const res: SessionStartResponse = await sessionsClient.startSession(input.childId, {
        planItemId: input.planItemId,
        clientTime,
      })

      sessionId.value = res.sessionId
      state.value = res.state
      hardStopAt.value = new Date(res.hardStopAt)
      resumeAt.value = res.resumeAt ? new Date(res.resumeAt) : null
      contentTitle.value = res.content?.title ?? ''
      startedAtClient.value = new Date(clientTime)
      tickNow.value = new Date()

      sessionStore.setCurrentSession({
        sessionId: res.sessionId,
        childId: input.childId,
        planItemId: input.planItemId,
        state: res.state,
        hardStopAt: res.hardStopAt,
        resumeAt: res.resumeAt,
        startedAtClient: clientTime,
      })

      startTicking()
      startHeartbeat()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to start session'
    } finally {
      busy.value = false
    }
  }

  async function end(reason: 'COMPLETED' | 'TIME_LIMIT' | 'PARENT_STOP' | 'EXIT') {
    if (!sessionId.value) {
      state.value = 'ENDED'
      return
    }
    busy.value = true
    try {
      await sessionsClient.endSession(sessionId.value, { reason })
    } catch {
      // If offline, the server will still enforce; UI can still end locally.
    } finally {
      state.value = 'ENDED'
      stopHeartbeat()
      stopTicking()
      sessionStore.clearSession()
      busy.value = false
    }
  }

  const view = computed<MachineView | null>(() => {
    if (!hardStopAt.value) return null

    const now = tickNow.value
    const toHardStop = clampNonNegative((hardStopAt.value.getTime() - now.getTime()) / 1000)
    const toResume = resumeAt.value ? clampNonNegative((resumeAt.value.getTime() - now.getTime()) / 1000) : null
    return {
      state: state.value,
      hardStopAt: hardStopAt.value,
      resumeAt: resumeAt.value ?? undefined,
      now,
      secondsToHardStop: toHardStop,
      secondsToResume: toResume,
      contentTitle: contentTitle.value,
    }
  })

  onBeforeUnmount(() => {
    stopHeartbeat()
    stopTicking()
  })

  return {
    busy,
    error,
    sessionId,
    state,
    view,
    start,
    end,
  }
}
