import {createRouter, createWebHistory} from 'vue-router'
import AppList from '../views/AppList.vue'
import AppDetail from '../views/AppDetail.vue'
import CompareWorkspace from '../views/CompareWorkspace.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {path: '/', component: AppList},
        {path: '/compare', component: CompareWorkspace},
        {path: '/app/:id', component: AppDetail},
        {path: '/app/:id/sql', component: AppDetail},
        {path: '/app/:id/sql/:executionId', component: AppDetail},
        {path: '/app/:id/jobs', component: AppDetail},
        {path: '/app/:id/stages', component: AppDetail},
        {path: '/app/:id/executors', component: AppDetail},
        {path: '/app/:id/storage', component: AppDetail},
        {path: '/app/:id/environment', component: AppDetail},
        {path: '/app/:id/job/:jobId', component: AppDetail},
        {path: '/app/:id/stage/:stageId', component: AppDetail}
    ]
})

export default router
