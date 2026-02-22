import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authClient from '../../api/clients/auth-client'

type Role = 'PARENT' | 'CHILD' | 'MODERATOR' | 'ADMIN'

const STORAGE_KEY = 'gg.auth.v1'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const userId = ref<string | null>(null)
  const householdId = ref<string | null>(null)
  const role = ref<Role | null>(null)
  const hydrated = ref(false)

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist() {
    const payload = {
      accessToken: accessToken.value,
      refreshToken: refreshToken.value,
      userId: userId.value,
      householdId: householdId.value,
      role: role.value,
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  }

  function clear() {
    accessToken.value = null
    refreshToken.value = null
    userId.value = null
    householdId.value = null
    role.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  function hydrateFromStorageOnce() {
    if (hydrated.value) return
    hydrated.value = true
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      const parsed = JSON.parse(raw) as Partial<{
        accessToken: string | null
        refreshToken: string | null
        userId: string | null
        householdId: string | null
        role: Role | null
      }>
      accessToken.value = parsed.accessToken ?? null
      refreshToken.value = parsed.refreshToken ?? null
      userId.value = parsed.userId ?? null
      householdId.value = parsed.householdId ?? null
      role.value = parsed.role ?? null
    } catch {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  async function login(email: string, password: string) {
    const res = await authClient.login({ email, password })
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    userId.value = res.userId
    householdId.value = res.householdId
    role.value = 'PARENT'
    try {
      const me = await authClient.me(res.accessToken)
      role.value = (me.role as Role) ?? role.value
      householdId.value = me.householdId ?? householdId.value
      userId.value = me.userId ?? userId.value
    } catch {
      // keep fallback values
    }
    persist()
  }

  async function register(email: string, password: string, displayName: string) {
    const res = await authClient.register({ email, password, displayName })
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    userId.value = res.userId
    householdId.value = res.householdId
    role.value = 'PARENT'
    try {
      const me = await authClient.me(res.accessToken)
      role.value = (me.role as Role) ?? role.value
      householdId.value = me.householdId ?? householdId.value
      userId.value = me.userId ?? userId.value
    } catch {
      // keep fallback values
    }
    persist()
  }

  async function refresh() {
    if (!refreshToken.value) return
    const res = await authClient.refresh({ refreshToken: refreshToken.value })
    accessToken.value = res.accessToken
    refreshToken.value = res.refreshToken
    persist()
  }

  async function logout() {
    try {
      if (refreshToken.value) {
        await authClient.logout({ refreshToken: refreshToken.value })
      }
    } finally {
      clear()
    }
  }

  return {
    accessToken,
    refreshToken,
    userId,
    householdId,
    role,
    isAuthenticated,
    hydrateFromStorageOnce,
    login,
    register,
    refresh,
    logout,
    clear,
  }
})
