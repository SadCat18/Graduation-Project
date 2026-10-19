import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BoardFields from '../components/board/BoardFields.vue'
import { basicFields, newDraft, recordPayload } from './adminFields'

describe('REFERENCE-01-FIX-R1 optional pricing mode request boundary', () => {
  it.each(Object.keys(basicFields))('%s new draft omits unfilled mode for create and patch', resource => {
    const draft = { ...newDraft(resource), key:'SYNTHETIC-DRAFT', version:3 }
    for (const mode of [null, '', undefined]) {
      const record = { ...draft, pricingMode:mode }
      const before = JSON.stringify(record)
      for (const editing of [false, true]) {
        const payload = recordPayload(record, editing)
        expect(payload).not.toHaveProperty('pricingMode')
        expect(JSON.parse(JSON.stringify(payload))).not.toHaveProperty('pricingMode')
        if (editing) expect(payload.expectedVersion).toBe(3)
        else expect(payload).not.toHaveProperty('expectedVersion')
      }
      expect(JSON.stringify(record)).toBe(before)
    }
  })
  it.each(Object.keys(basicFields))('%s cleared form choice omits mode without choosing an implicit default', async resource => {
    for (const mode of ['REFERENCE', 'EXACT']) {
      const record = { ...newDraft(resource), key:'SYNTHETIC-CLEAR', pricingMode:mode, version:4, priceAssumptions:['保留原假设'] }
      const wrapper = mount(BoardFields, {props:{modelValue:record, fields:basicFields[resource].filter(f=>f.key==='pricingMode')}})
      try {
        await wrapper.get('select').setValue('')
        const cleared = wrapper.emitted('update:modelValue')[0][0]
        expect(cleared.pricingMode).toBeNull()
        for (const editing of [false, true]) {
          const payload = recordPayload(cleared, editing)
          expect(payload).not.toHaveProperty('pricingMode')
          expect(payload.priceAssumptions).toEqual(['保留原假设'])
          if (editing) expect(payload.expectedVersion).toBe(4)
        }
        expect(record.pricingMode).toBe(mode)
      } finally { wrapper.unmount() }
    }
  })
  it.each(Object.keys(basicFields))('%s preserves explicit modes and leaves invalid nonempty values to backend validation', resource => {
    for (const mode of ['REFERENCE','EXACT','INVALID_MODE']) {
      const record = { ...newDraft(resource), pricingMode:mode, version:5, active:true, priceAssumptions:['原资料假设'], raw:{keep:true} }
      for (const editing of [false, true]) {
        expect(recordPayload(record, editing)).toMatchObject({pricingMode:mode,priceAssumptions:['原资料假设'],raw:{keep:true}})
        expect(recordPayload(record, editing)).not.toHaveProperty('active')
      }
    }
  })
})
