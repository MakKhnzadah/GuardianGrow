import type { Router } from 'vue-router'
import { useAuthStore } from '../features/auth/store'

type AllowedRole = 'PARENT' | 'CHILD' | 'MODERATOR' | 'ADMIN'

export function installAuthGuard(router: Router) {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    auth.hydrateFromStorageOnce()

    const isPublic = Boolean(to.meta.public)
    const requiresAuth = Boolean(to.meta.requiresAuth)
    const roles = (to.meta.roles as AllowedRole[] | undefined) ?? undefined

    if (isPublic && !requiresAuth) return true
    if (!requiresAuth) return true

    if (!auth.isAuthenticated) {
      return { path: '/login', query: { next: to.fullPath } }
    }

    if (roles?.length) {
      const role = auth.role
      if (!role || !roles.includes(role as AllowedRole)) {
        return { path: '/login' }
      }
    }

    return true
  })
}
