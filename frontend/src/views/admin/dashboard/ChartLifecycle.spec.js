import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Trend from './AdminTrendChart.vue'
import Donut from './AdminDonutChart.vue'
import Funnel from './AdminFunnelChart.vue'

const { instances, init } = vi.hoisted(() => {
  const instances = []
  return { instances, init:vi.fn(element => {
    const instance = {element,setOption:vi.fn(),resize:vi.fn(),dispose:vi.fn()}
    instances.push(instance)
    return instance
  }) }
})
vi.mock('./echarts', () => ({default:{init}}))
const observers = []
let wrapper
beforeEach(() => {
  instances.length = 0; observers.length = 0; init.mockClear()
  vi.stubGlobal('ResizeObserver', class {
    constructor(callback) { this.callback=callback; this.observe=vi.fn(); this.disconnect=vi.fn(); observers.push(this) }
  })
})
afterEach(() => { wrapper?.unmount(); document.body.innerHTML=''; vi.unstubAllGlobals() })

describe('chart DOM lifecycle with mocked ECharts and ResizeObserver, not actual canvas', () => {
  it.each([
    ['trend',Trend,{rows:[{date:'2026-10-19',newUsers:3}]},{rows:[]}],
    ['donut',Donut,{items:[{type:'采集分类',count:3}]},{items:[]}],
    ['funnel',Funnel,{conversion:{views:10,signs:3,rate:30}},{conversion:{}}]
  ])('%s binds each live container and disposes removed containers/observers', async (_name, Component, data, empty) => {
    wrapper = mount(Component,{attachTo:document.body,props:{...data,loading:true},global:{stubs:{AppIcon:true}}})
    await flushPromises(); expect(init).not.toHaveBeenCalled(); expect(observers).toHaveLength(0)
    await wrapper.setProps({loading:false}); await flushPromises()
    expect(instances).toHaveLength(1); expect(instances[0].setOption).toHaveBeenCalled()
    expect(observers[0].observe).toHaveBeenCalledWith(instances[0].element)
    observers[0].callback(); expect(instances[0].resize).toHaveBeenCalledOnce()

    for (const hidden of [empty,{error:'合成加载失败'},{loading:true}]) {
      const old = instances.at(-1), observer = observers.at(-1)
      await wrapper.setProps(hidden); await flushPromises()
      expect(old.dispose).toHaveBeenCalledOnce(); expect(observer.disconnect).toHaveBeenCalledOnce()
      expect(old.element.isConnected).toBe(false)
      await wrapper.setProps({...data,error:'',loading:false}); await flushPromises()
      const current = instances.at(-1), currentObserver = observers.at(-1)
      expect(current).not.toBe(old); expect(current.element).not.toBe(old.element)
      expect(currentObserver.observe).toHaveBeenCalledWith(current.element)
      observer.callback(); expect(current.resize).not.toHaveBeenCalled()
      currentObserver.callback(); expect(current.resize).toHaveBeenCalledOnce()
    }
    const last=instances.at(-1), observer=observers.at(-1)
    wrapper.unmount(); wrapper=null
    expect(last.dispose).toHaveBeenCalledOnce(); expect(observer.disconnect).toHaveBeenCalledOnce()
    for (const instance of instances) expect(instance.dispose).toHaveBeenCalledOnce()
  })
})
