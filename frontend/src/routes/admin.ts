import type { RouteRecordRaw } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import ModerationQueuePage from '../features/moderation/pages/ModerationQueuePage.vue'
import ModerationItemPage from '../features/moderation/pages/ModerationItemPage.vue'
import AdminReportsPage from '../features/moderation/pages/AdminReportsPage.vue'
import AdminUsersPage from '../features/admin/pages/AdminUsersPage.vue'
import AdminAuditPage from '../features/admin/pages/AdminAuditPage.vue'

export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, roles: ['MODERATOR', 'ADMIN'] },
    children: [
      { path: 'moderation', component: ModerationQueuePage },
      { path: 'moderation/:queueItemId', component: ModerationItemPage },
      { path: 'reports', component: AdminReportsPage },
      { path: 'users', component: AdminUsersPage, meta: { requiresAuth: true, roles: ['ADMIN'] } },
      { path: 'audit', component: AdminAuditPage, meta: { requiresAuth: true, roles: ['ADMIN'] } },
      { path: '', redirect: '/admin/moderation' },
    ],
  },
]
