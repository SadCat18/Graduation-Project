import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminReviewPanel from './AdminReviewPanel.vue'

const reviewItem = {
  id: 'activity-1',
  type: 'activity',
  typeLabel: '活动',
  status: '0',
  statusLabel: '待审核',
  title: '周末滑板活动',
  actor: '小林',
  time: '2026-07-15 18:00',
  summary: '周末一起刷街',
  fullContent: '周末一起刷街，记得携带护具。',
  facts: [{ label: '城市', value: '杭州' }],
  detailFacts: [{ label: '活动ID', value: '1' }],
  historyNote: ''
}

function mountPanel(overrides = {}) {
  return mount(AdminReviewPanel, {
    props: {
      pendingReviewTotal: 1,
      pendingCounts: { activity: 1, bulletin: 0, report: 0, placeContribution: 0, placeReview: 0 },
      tabs: [{ key: 'all', label: '全部' }, { key: 'activity', label: '活动' }],
      filteredCards: [reviewItem],
      selectedCards: [],
      selectedIds: [],
      ...overrides
    }
  })
}

describe('AdminReviewPanel', () => {
  it('renders review counts and cards from parent data', () => {
    const wrapper = mountPanel()

    expect(wrapper.text()).toContain('待办 1 条')
    expect(wrapper.text()).toContain('周末滑板活动')
    expect(wrapper.text()).toContain('杭州')
  })

  it('emits filter and tab model updates', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('select')[0].setValue('pending')
    await wrapper.get('input[type="text"]').setValue('杭州')
    await wrapper.findAll('.review-tabs button')[1].trigger('click')

    expect(wrapper.emitted('update:statusFilter')[0]).toEqual(['pending'])
    expect(wrapper.emitted('update:keyword')[0]).toEqual(['杭州'])
    expect(wrapper.emitted('update:centerTab')[0]).toEqual(['activity'])
  })

  it('emits selection and shows the complete batch summary', async () => {
    const wrapper = mountPanel({ canBatchApproveOrReject: true, selectedCards: [reviewItem] })
    const checkboxes = wrapper.findAll('input[type="checkbox"]')

    await checkboxes[0].setValue(true)
    await checkboxes[1].setValue(true)
    await wrapper.findAll('button').find(button => button.text() === '批量通过').trigger('click')

    expect(wrapper.emitted('toggleSelectAll')[0]).toEqual([true])
    expect(wrapper.emitted('toggleSelect')[0]).toEqual(['activity-1', true])
    expect(wrapper.emitted('batchReview')[0]).toEqual(['approve'])
    expect(wrapper.text()).toContain('已选 1 条 · 活动 · 待审核 1 条')
  })

  it('delegates card decisions and AI moderation to the parent', async () => {
    const wrapper = mountPanel()

    await wrapper.findAll('button').find(button => button.text() === '通过').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === 'AI 审核建议').trigger('click')

    expect(wrapper.emitted('action')[0]).toEqual([reviewItem, 'approve'])
    expect(wrapper.emitted('moderation')[0]).toEqual([reviewItem])
  })

  it('renders the drawer and emits template and close actions', async () => {
    const wrapper = mountPanel({
      drawerVisible: true,
      drawerItem: reviewItem,
      drawerTemplates: [{ key: 'incomplete', label: '信息不完整', actions: ['reject'] }],
      moderationResultKey: reviewItem.id,
      moderationResult: {
        riskLevel: 'LOW',
        suggestion: '补充信息',
        normalizedSummary: '内容基本安全',
        riskPoints: []
      }
    })

    await wrapper.get('button.template-chip').trigger('click')
    await wrapper.findAll('.review-drawer-head button')[0].trigger('click')

    expect(wrapper.text()).toContain('内容基本安全')
    expect(wrapper.emitted('action')[0]).toEqual([reviewItem, 'reject', 'incomplete'])
    expect(wrapper.emitted('closeDrawer')).toHaveLength(1)
  })
})
