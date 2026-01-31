import { createRouter, createWebHistory } from 'vue-router'
import AppList from '../views/AppList.vue'
import AppDetail from '../views/AppDetail.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: AppList },
    { path: '/app/:id', component: AppDetail },
    { path: '/app/:id/stage/:stageId', component: AppDetail } // 共享 AppDetail 组件，但带上 stageId
  ]
})

export default router
