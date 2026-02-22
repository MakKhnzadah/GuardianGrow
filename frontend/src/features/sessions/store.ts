import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { SessionState } from '../../api/clients/sessions-client'

type SessionUi = {
  sessionId: string
  childId: string
  planItemId: string
  state: SessionState
  hardStopAt: string
  resumeAt?: string
  startedAtClient: string
}

export const useSessionStore = defineStore('session', () => {
  const selectedChildId = ref<string | null>(null)
  const current = ref<SessionUi | null>(null)

  const hasActiveSession = computed(() => Boolean(current.value && current.value.state !== 'ENDED'))

  function selectChild(childId: string) {
    selectedChildId.value = childId
  }

  function setCurrentSession(value: SessionUi) {
    current.value = value
  }

  function clearSession() {
    current.value = null
  }

  return {
    selectedChildId,
    current,
    hasActiveSession,
    selectChild,
    setCurrentSession,
    clearSession,
  }
})
