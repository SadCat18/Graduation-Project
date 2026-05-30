                                                                                                                                                                                                                                                                                                                   import { createRouter, createWebHistory } from 'vue-router'
import { getRole, getToken } from '../utils/auth'

import LoginPage from '../views/LoginPage.vue'
import HomePage from '../views/HomePage.vue'
import CommunityPage from '../views/CommunityPage.vue'
import PostDetailPage from '../views/PostDetailPage.vue'
import PostPublishPage from '../views/PostPublishPage.vue'
import ActivitiesPage from '../views/ActivitiesPage.vue'
import ActivityPublishPage from '../views/ActivityPublishPage.vue'
import BulletinListPage from '../views/BulletinListPage.vue'
import BulletinDetailPage from '../views/BulletinDetailPage.vue'
import BulletinPublishPage from '../views/BulletinPublishPage.vue'
import NewsDetailPage from '../views/NewsDetailPage.vue'
import ProfilePage from '../views/ProfilePage.vue'
import AdminPage from '../views/AdminPage.vue'
import PlaceDetailPage from '../views/PlaceDetailPage.vue'

const routes = [
  { path: '/login', component: LoginPage },
  { path: '/register', component: LoginPage },
  { path: '/', component: HomePage },
  { path: '/community', component: CommunityPage },
  { path: '/community/post/:id', component: PostDetailPage },
  { path: '/community/publish', component: PostPublishPage, meta: { requiresAuth: true } },
  { path: '/activities', component: ActivitiesPage },
  { path: '/activities/publish', component: ActivityPublishPage, meta: { requiresAuth: true } },
  { path: '/places/:id', component: PlaceDetailPage },
  { path: '/bulletins', component: BulletinListPage },
  { path: '/bulletins/:id', component: BulletinDetailPage },
  { path: '/bulletins/publish', component: BulletinPublishPage, meta: { requiresAuth: true } },
  { path: '/news/:id', component: NewsDetailPage },
  { path: '/profile', component: ProfilePage, meta: { requiresAuth: true } },
  { path: '/admin', component: AdminPage, meta: { requiresAuth: true, adminOnly: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = getToken()
  if ((to.path === '/login' || to.path === '/register') && token) {
    return '/'
  }
  if (to.meta.requiresAuth && !token) {
    return '/login'
  }
  if (to.meta.adminOnly && getRole() !== 'ADMIN') {
    return '/login'
  }
  return true
})

export default router
