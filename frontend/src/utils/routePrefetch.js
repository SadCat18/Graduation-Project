const warmedChunkKeys = new Set()

const routeWarmers = [
  {
    key: 'home',
    load: () => import(/* webpackChunkName: "view-home" */ '../views/HomePage.vue')
  },
  {
    key: 'community',
    load: () => import(/* webpackChunkName: "view-community" */ '../views/CommunityPage.vue')
  },
  {
    key: 'activities',
    load: () => import(/* webpackChunkName: "view-activities" */ '../views/ActivitiesPage.vue')
  },
  {
    key: 'bulletins',
    load: () => import(/* webpackChunkName: "view-bulletins" */ '../views/BulletinListPage.vue')
  }
]

export function warmLikelyRouteChunks() {
  const tasks = []

  for (const warmer of routeWarmers) {
    if (warmedChunkKeys.has(warmer.key)) continue
    warmedChunkKeys.add(warmer.key)
    tasks.push(warmer.load().catch(() => {}))
  }

  return Promise.allSettled(tasks)
}
