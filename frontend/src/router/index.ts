import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/chat',
      children: [
        { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { eyebrow: 'ASK', title: '论文问答' } },
        { path: 'papers', name: 'papers', component: () => import('../views/PapersView.vue'), meta: { eyebrow: 'INDEX', title: '论文知识库' } },
        { path: 'research', name: 'research', component: () => import('../views/ResearchView.vue'), meta: { eyebrow: 'FLOW', title: '研究工作流' } },
        { path: 'runtime', name: 'runtime', component: () => import('../views/RuntimeView.vue'), meta: { eyebrow: 'TRACE', title: 'Agent Runtime' } },
      ],
    },
  ],
})

export default router
