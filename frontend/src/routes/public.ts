import type { RouteRecordRaw } from 'vue-router'
import PublicLayout from '../layouts/PublicLayout.vue'
import LoginPage from '../features/auth/pages/LoginPage.vue'
import RegisterPage from '../features/auth/pages/RegisterPage.vue'
import ShareReportPage from '../features/share/pages/ShareReportPage.vue'

export const publicRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: PublicLayout,
    meta: { public: true },
    children: [{ path: '', component: LoginPage }],
  },
  {
    path: '/register',
    component: PublicLayout,
    meta: { public: true },
    children: [{ path: '', component: RegisterPage }],
  },
  {
    path: '/share/:token',
    component: PublicLayout,
    meta: { public: true },
    children: [{ path: '', component: ShareReportPage }],
  },
]
