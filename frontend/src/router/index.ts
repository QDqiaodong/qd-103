import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('../views/HomePage.vue')
    },
    {
      path: '/create',
      name: 'create',
      component: () => import('../views/CreatePage.vue')
    },
    {
      path: '/edit/:id',
      name: 'edit',
      component: () => import('../views/EditPage.vue')
    },
    {
      path: '/fill/:id',
      name: 'fill',
      component: () => import('../views/FillPage.vue')
    },
    {
      path: '/statistics/:id',
      name: 'statistics',
      component: () => import('../views/StatisticsPage.vue')
    },
    {
      path: '/preview/:id',
      name: 'preview',
      component: () => import('../views/PreviewPage.vue')
    }
  ]
})

export default router
