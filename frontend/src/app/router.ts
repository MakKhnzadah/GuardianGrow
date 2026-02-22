import { createRouter, createWebHistory, type Router } from 'vue-router'
import { installAuthGuard } from './router-guard'
import { adminRoutes } from '../routes/admin'
import { childRoutes } from '../routes/child'
import { parentRoutes } from '../routes/parent'
import { publicRoutes } from '../routes/public'

export function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      ...publicRoutes,
      ...parentRoutes,
      ...childRoutes,
      ...adminRoutes,
      {
        path: '/:pathMatch(.*)*',
        redirect: '/login',
      },
    ],
  })

  installAuthGuard(router)
  return router
}
