import type { RouteRecordRaw } from 'vue-router'
import ParentLayout from '../layouts/ParentLayout.vue'
import ParentDashboardPage from '../features/parent/pages/ParentDashboardPage.vue'
import ParentChildrenPage from '../features/children/pages/ParentChildrenPage.vue'
import ParentChildProfilePage from '../features/children/pages/ParentChildProfilePage.vue'
import ParentPlanBuilderPage from '../features/plans/pages/ParentPlanBuilderPage.vue'
import ParentLibraryPage from '../features/content/pages/ParentLibraryPage.vue'
import ParentContentPreviewPage from '../features/content/pages/ParentContentPreviewPage.vue'
import ParentReportsPage from '../features/reports/pages/ParentReportsPage.vue'
import ParentSharingPage from '../features/share/pages/ParentSharingPage.vue'

export const parentRoutes: RouteRecordRaw[] = [
  {
    path: '/parent',
    component: ParentLayout,
    meta: { requiresAuth: true, roles: ['PARENT', 'ADMIN'] },
    children: [
      { path: 'dashboard', component: ParentDashboardPage },
      { path: 'children', component: ParentChildrenPage },
      { path: 'children/:childId', component: ParentChildProfilePage },
      { path: 'children/:childId/plan', component: ParentPlanBuilderPage },
      { path: 'library', component: ParentLibraryPage },
      { path: 'content/:contentId', component: ParentContentPreviewPage },
      { path: 'reports/:childId', component: ParentReportsPage },
      { path: 'sharing/:childId', component: ParentSharingPage },
      { path: '', redirect: '/parent/dashboard' },
    ],
  },
]
