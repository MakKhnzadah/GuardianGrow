import type { RouteRecordRaw } from 'vue-router'
import ChildLayout from '../layouts/ChildLayout.vue'
import ChildSelectPage from '../features/children/pages/ChildSelectPage.vue'
import ChildHomePage from '../features/sessions/pages/ChildHomePage.vue'
import ChildSessionPage from '../features/sessions/pages/ChildSessionPage.vue'
import ChildDonePage from '../features/sessions/pages/ChildDonePage.vue'

export const childRoutes: RouteRecordRaw[] = [
  {
    path: '/child',
    component: ChildLayout,
    meta: { requiresAuth: true, roles: ['CHILD', 'PARENT', 'ADMIN'] },
    children: [
      { path: 'select', component: ChildSelectPage },
      { path: ':childId/home', component: ChildHomePage },
      { path: ':childId/session/:planItemId', component: ChildSessionPage },
      { path: ':childId/done', component: ChildDonePage },
    ],
  },
]
