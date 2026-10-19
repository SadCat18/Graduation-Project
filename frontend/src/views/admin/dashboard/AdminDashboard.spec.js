import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminDashboard from './AdminDashboard.vue'
import AdminHealthStrip from './AdminHealthStrip.vue'
import AdminKpiCard from './AdminKpiCard.vue'
import AdminPriorityQueue from './AdminPriorityQueue.vue'
import AdminTrendChart from './AdminTrendChart.vue'
import AdminFunnelChart from './AdminFunnelChart.vue'

const analytics = {
  trend7d: [
    { date: '2026-07-10', newUsers: 4, newPosts: 3, newActivities: 2, newSigns: 5 },
    { date: '2026-07-11', newUsers: 5, newPosts: 4, newActivities: 2, newSigns: 6 }
  ],
  previousTrend7d: [
    { date: '2026-07-03', newUsers: 8, newPosts: 3, newActivities: 2, newSigns: 8 },
    { date: '2026-07-04', newUsers: 8, newPosts: 4, newActivities: 2, newSigns: 8 }
  ],
  trend30d: [{ date: '2026-07-11', newUsers: 30, newPosts: 20, newActivities: 8, newSigns: 40 }],
  previousTrend30d: [{ date: '2026-06-11', newUsers: 15, newPosts: 10, newActivities: 4, newSigns: 20 }],
  activityConversion: { views: 100, signs: 20, rate: 20 },
  activityConversion30d: { views: 500, signs: 125, rate: 25 },
  hotPosts: [{ postId: 1, title: '热门帖子', hotScore: 32 }],
  hotActivities: [],
  bulletinTypeRatio: []
}

function mountDashboard(overrides = {}) {
  const now = Date.now()
  return shallowMount(AdminDashboard, {
    props: {
      stats: { userTotal: 100, postTotal: 40, bulletinTotal: 10, newsTotal: 8, activityTotal: 12, signTotal: 25 },
      analytics,
      reviewActivities: [{ activityId: 1, reviewStatus: '0', createTime: new Date(now - 2 * 3600000).toISOString() }],
      reviewBulletins: [],
      reports: [
        { reportId: 1, targetType: 'POST', targetId: 9, targetTitle: '风险帖子', reason: '违法违规', createTime: new Date(now - 3 * 3600000).toISOString() },
        { reportId: 2, targetType: 'POST', targetId: 9, targetTitle: '风险帖子', reason: '广告', createTime: new Date(now - 3600000).toISOString() }
      ],
      pendingPlaces: [],
      placeReviews: [],
      quickActions: [{ key: 'review', label: '审核待办', icon: 'notice', tab: 'reviewCenter' }],
      updatedAt: new Date(now).toISOString(),
      ...overrides
    }
  })
}

describe('AdminDashboard', () => {
  it('maps pending work into a risk-first four-card queue', () => {
    const wrapper = mountDashboard()
    const priority = wrapper.findComponent(AdminPriorityQueue)
    const items = priority.props('items')

    expect(items).toHaveLength(4)
    expect(items[0].key).toBe('report')
    expect(items[0].tone).toBe('danger')
    expect(items[0].detail).toContain('重复举报')
  })

  it('renders the decision strip and four core KPI cards', () => {
    const wrapper = mountDashboard()
    const health = wrapper.findComponent(AdminHealthStrip)
    const kpis = wrapper.findAllComponents(AdminKpiCard)

    expect(health.props('pendingTotal')).toBe(3)
    expect(health.props('health').tone).toBe('danger')
    expect(kpis).toHaveLength(4)
    expect(kpis[0].props('item').value).toBe(100)
    expect(kpis[0].props('item').tone).toBe('danger')
  })

  it('forwards queue navigation to the page owner', () => {
    const wrapper = mountDashboard()
    wrapper.findComponent(AdminPriorityQueue).vm.$emit('select', { tab: 'reports' })
    expect(wrapper.emitted('navigate')[0]).toEqual([{ tab: 'reports' }])
  })

  it('switches trend, KPI comparison and funnel to the same 30-day range', async () => {
    const wrapper = mountDashboard()
    wrapper.findComponent(AdminHealthStrip).vm.$emit('update:rangeDays', 30)
    await wrapper.vm.$nextTick()

    expect(wrapper.findComponent(AdminTrendChart).props('rangeDays')).toBe(30)
    expect(wrapper.findComponent(AdminTrendChart).props('rows')).toEqual(analytics.trend30d)
    expect(wrapper.findComponent(AdminFunnelChart).props('conversion')).toEqual(analytics.activityConversion30d)
    expect(wrapper.findAllComponents(AdminKpiCard)[0].props('item').summary).toContain('近 30 天')
  })
})
