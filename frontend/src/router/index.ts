import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import CommunityView from '../views/CommunityView.vue'
import SeniorsView from '../views/SeniorsView.vue'
import SeniorDetailView from '../views/SeniorDetailView.vue'
import StudioView from '../views/StudioView.vue'
import MeView from '../views/MeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/community', component: CommunityView },
    { path: '/seniors', component: SeniorsView },
    { path: '/seniors/studio', component: StudioView },
    { path: '/seniors/:id', component: SeniorDetailView, props: true },
    { path: '/me', component: MeView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
