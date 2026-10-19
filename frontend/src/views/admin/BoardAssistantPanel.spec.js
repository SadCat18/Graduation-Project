import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Panel from './BoardAssistantPanel.vue'
import BoardFields from '../../components/board/BoardFields.vue'
const {api}=vi.hoisted(()=>({api:{records:vi.fn(),createRecord:vi.fn(),updateRecord:vi.fn(),activate:vi.fn(),trial:vi.fn()}}))
vi.mock('../../api/board',()=>({boardApi:api}))
const row={key:'TEST-P',model:'Synthetic',category:'DECK',version:2,active:false,sources:[{url:'https://example.invalid',note:'synthetic-only',unknown:'keep'}],notes:'fixture'}
let wrapper
const button=text=>wrapper.findAll('button').find(b=>b.text()===text)
describe('SYNTHETIC admin frontend gates',()=>{
  beforeEach(async()=>{vi.resetAllMocks();localStorage.setItem('skate_role','ADMIN');api.records.mockResolvedValue({list:[row],total:11});wrapper=mount(Panel);await flushPromises()})
  afterEach(()=>wrapper.unmount())
  it('activation HTTP200 failure preserves inactive record and issues',async()=>{
    api.activate.mockRejectedValue({errorCode:'SELECTION_INVALID',data:{errors:[{path:'sources',detail:'未核实来源'}]}})
    await button('启用TEST-P').trigger('click');await button('确认启停').trigger('click');await flushPromises()
    expect(api.activate).toHaveBeenCalledWith('products','TEST-P',{active:true,expectedVersion:2});expect(wrapper.text()).toContain('未核实来源');expect(wrapper.text()).not.toContain('记录已启用')
  })
  it('keeps unsaved fields on version conflict and submits expectedVersion',async()=>{
    await button('编辑TEST-P').trigger('click');api.updateRecord.mockRejectedValue({errorCode:'VERSION_CONFLICT',data:{currentVersion:3}})
    await wrapper.find('form').trigger('submit');await flushPromises()
    expect(api.updateRecord.mock.calls[0][2]).toMatchObject({expectedVersion:2,sources:[{unknown:'keep'}]});expect(api.updateRecord.mock.calls[0][2]).not.toHaveProperty('active');expect(wrapper.text()).toContain('未保存编辑仍保留')
  })
  it('all five resources are paged and drafts have no invented threshold/source',async()=>{
    await button('下一页资源').trigger('click');await flushPromises();expect(api.records).toHaveBeenLastCalledWith('products',{page:2,size:10})
    await button('搭配规则').trigger('click');await flushPromises();await button('新增搭配规则草稿').trigger('click')
    const editor=wrapper.findAllComponents(BoardFields)[0];editor.vm.$emit('update:modelValue',{key:'TEST-R',type:'WHEEL_TRUCK_RISER',scope:{},conditions:{},outcome:{},sources:[]});await flushPromises()
    expect(wrapper.text()).toContain('轮径下限（mm）');expect(wrapper.text()).toContain('未知数值留空')
    api.createRecord.mockResolvedValue({key:'TEST-R',version:1,sources:[],active:false});await wrapper.find('form').trigger('submit');await flushPromises();expect(api.createRecord.mock.calls[0][1].conditions).toEqual({});expect(api.createRecord.mock.calls[0][1].sources).toEqual([])
  })
  it('refuses non-admin before requesting records',async()=>{
    wrapper.unmount();api.records.mockClear();localStorage.setItem('skate_role','USER');wrapper=mount(Panel);await flushPromises();expect(api.records).not.toHaveBeenCalled();expect(wrapper.text()).toContain('仅限管理员')
  })
})
