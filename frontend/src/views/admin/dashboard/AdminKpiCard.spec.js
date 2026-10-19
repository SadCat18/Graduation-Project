import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AdminKpiCard from './AdminKpiCard.vue'

describe('AdminKpiCard', () => {
  it('renders a textual trend summary and emits its action', async () => {
    const action = { tab: 'analytics' }
    const wrapper = mount(AdminKpiCard, {
      props: {
        item: {
          label: '报名总量', value: 128, deltaDisplay: '-24%', tone: 'danger', icon: 'notice',
          series: [8, 7, 9, 5, 4, 3, 2], summary: '较上期明显下降', action
        }
      }
    })

    expect(wrapper.text()).toContain('报名总量')
    expect(wrapper.text()).toContain('-24%')
    expect(wrapper.text()).toContain('较上期明显下降')
    expect(wrapper.get('polyline').attributes('points')).not.toBe('')

    await wrapper.trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual([action])
  })
})
