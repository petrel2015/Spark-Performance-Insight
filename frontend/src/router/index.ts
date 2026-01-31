import { createRouter, createWebHistory } from 'vue-router'
import AppList from '../views/AppList.vue'
import AppDetail from '../views/AppDetail.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: AppList },
    { path: '/app/:id', component: AppDetail }
  ]
})

export default router
