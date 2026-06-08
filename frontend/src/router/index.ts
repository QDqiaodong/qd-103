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
      path: '/fingerprint/:id',
      name: 'fingerprint',
      component: () => import('../views/FingerprintPage.vue')
    },
    {
      path: '/preview/:id',
      name: 'preview',
      component: () => import('../views/PreviewPage.vue')
    },
    {
      path: '/snapshot/:id',
      name: 'snapshot',
      component: () => import('../views/SnapshotPage.vue')
    },
    {
      path: '/share/:id',
      name: 'share',
      component: () => import('../views/SharePage.vue')
    },
    {
      path: '/share/snapshot/:id',
      name: 'share-snapshot',
      component: () => import('../views/SharePage.vue')
    }
  ]
})

export default router
