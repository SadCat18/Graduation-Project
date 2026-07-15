const warmedChunkKeys = new Set()

const routeWarmers = new Map([
  ['/', {
    key: '/',
    load: () => import(/* webpackChunkName: "view-home" */ '../views/HomePage.vue')
  }],
  ['/community', {
    key: '/community',
    load: () => import(/* webpackChunkName: "view-community" */ '../views/CommunityPage.vue')
  }],
  ['/activities', {
    key: '/activities',
    load: () => import(/* webpackChunkName: "view-activities" */ '../views/ActivitiesPage.vue')
  }],
  ['/bulletins', {
    key: '/bulletins',
    load: () => import(/* webpackChunkName: "view-bulletins" */ '../views/BulletinListPage.vue')
  }],
  ['/skate', {
    key: '/skate',
    load: () => import(/* webpackChunkName: "view-skate" */ '../views/SkateProfessionalPage.vue')
  }],
  ['/ai-coach', {
    key: '/ai-coach',
    load: () => import(/* webpackChunkName: "view-ai-coach" */ '../views/AiCoachPage.vue')
  }]
])

export function warmRouteChunk(path) {
  const warmer = routeWarmers.get(path)
  if (!warmer || warmedChunkKeys.has(warmer.key)) return Promise.resolve()
  warmedChunkKeys.add(warmer.key)
  return warmer.load().catch(() => {})
}
