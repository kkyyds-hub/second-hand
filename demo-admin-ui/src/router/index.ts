import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '',
          name: 'Dashboard',
          component: () => import('@/pages/Dashboard.vue')
        },
        {
          path: 'users',
          name: 'UserList',
          component: () => import('@/pages/users/UserList.vue')
        }
      ]
    }
  ]
})

export default router
