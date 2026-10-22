import { createRouter, createWebHistory } from 'vue-router'
import { getRole, getToken } from '../utils/auth'
import { safeBoardReturn } from '../board/state'

const LoginPage = () => import(/* webpackChunkName: "view-login" */ '../views/LoginPage.vue')
const HomePage = () => import(/* webpackChunkName: "view-home" */ '../views/HomePage.vue')
const CommunityPage = () => import(/* webpackChunkName: "view-community" */ '../views/CommunityPage.vue')
const PostDetailPage = () => import(/* webpackChunkName: "view-community" */ '../views/PostDetailPage.vue')
const PostPublishPage = () => import(/* webpackChunkName: "view-community" */ '../views/PostPublishPage.vue')
const ActivitiesPage = () => import(/* webpackChunkName: "view-activities" */ '../views/ActivitiesPage.vue')
const ActivityDetailPage = () => import(/* webpackChunkName: "view-activities" */ '../views/ActivityDetailPage.vue')
const ActivityPublishPage = () => import(/* webpackChunkName: "view-activities" */ '../views/ActivityPublishPage.vue')
const BulletinListPage = () => import(/* webpackChunkName: "view-bulletins" */ '../views/BulletinListPage.vue')
const BulletinDetailPage = () => import(/* webpackChunkName: "view-bulletins" */ '../views/BulletinDetailPage.vue')
const BulletinPublishPage = () => import(/* webpackChunkName: "view-bulletins" */ '../views/BulletinPublishPage.vue')
const NewsDetailPage = () => import(/* webpackChunkName: "view-news" */ '../views/NewsDetailPage.vue')
const ProfilePage = () => import(/* webpackChunkName: "view-profile" */ '../views/ProfilePage.vue')
const UserProfilePage = () => import(/* webpackChunkName: "view-user-profile" */ '../views/UserProfilePage.vue')
const MessagesPage = () => import(/* webpackChunkName: "view-messages" */ '../views/MessagesPage.vue')
const AdminPage = () => import(/* webpackChunkName: "view-admin" */ '../views/AdminPage.vue')
const PlaceDetailPage = () => import(/* webpackChunkName: "view-places" */ '../views/PlaceDetailPage.vue')
const AiCoachPage = () => import(/* webpackChunkName: "view-ai-coach" */ '../views/AiCoachPage.vue')
const VideoWatchPage = () => import(/* webpackChunkName: "view-videos" */ '../views/VideoWatchPage.vue')
const SkateProfessionalPage = () => import(/* webpackChunkName: "view-skate" */ '../views/SkateProfessionalPage.vue')

const routes = [
  { path: '/training-guidance', component: () => import('../views/TrainingGuidancePage.vue'), meta: { requiresAuth: true } },
  { path: '/admin/training-guidance', component: () => import('../views/admin/TrainingGuidanceAdminPage.vue'), meta: { requiresAuth: true, adminOnly: true, wideLayout: true } },
  { path: '/board-assistant', component: () => import('../views/BoardAssistantPage.vue') },
  { path: '/profile/board-plans', component: () => import('../views/BoardPlansPage.vue'), meta: { requiresAuth: true } },
  { path: '/login', component: LoginPage },
  { path: '/register', component: LoginPage },
  { path: '/', component: HomePage },
  { path: '/community', component: CommunityPage },
  { path: '/community/post/:id', component: PostDetailPage },
  { path: '/community/publish', component: PostPublishPage, meta: { requiresAuth: true } },
  { path: '/activities', component: ActivitiesPage },
  { path: '/activities/publish', component: ActivityPublishPage, meta: { requiresAuth: true } },
  { path: '/activities/:id', component: ActivityDetailPage },
  { path: '/skate', component: SkateProfessionalPage },
  { path: '/places/:id', component: PlaceDetailPage },
  { path: '/bulletins', component: BulletinListPage },
  { path: '/bulletins/:id', component: BulletinDetailPage },
  { path: '/bulletins/publish', component: BulletinPublishPage, meta: { requiresAuth: true } },
  { path: '/news/:id', component: NewsDetailPage },
  { path: '/videos/:id', component: VideoWatchPage },
  { path: '/users/:id', component: UserProfilePage },
  { path: '/messages', component: MessagesPage, meta: { requiresAuth: true, wideLayout: true } },
  { path: '/messages/:conversationId', component: MessagesPage, meta: { requiresAuth: true, wideLayout: true } },
  { path: '/ai-coach', component: AiCoachPage, meta: { requiresAuth: true } },
  { path: '/profile', component: ProfilePage, meta: { requiresAuth: true } },
  { path: '/admin', component: AdminPage, meta: { requiresAuth: true, adminOnly: true, wideLayout: true } }
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
    const returnTo = safeBoardReturn(to.path)
    if (returnTo) return { path: '/login', query: { returnTo } }
    return '/login'
  }
  if (to.meta.adminOnly && getRole() !== 'ADMIN') {
    return '/login'
  }
  return true
})

export default router
