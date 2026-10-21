import { afterEach, expect, it, vi } from 'vitest'
afterEach(()=>{vi.unstubAllEnvs();vi.useRealTimers();delete window.AMap;document.querySelectorAll('script[data-amap-loader]').forEach(s=>s.remove());vi.resetModules()})
it('map SDK rejection clears failed promise/script; explicit retry can succeed without region requests',async()=>{
 vi.resetModules();vi.stubEnv('VUE_APP_AMAP_KEY','SYNTHETIC_LOCAL_NO_NETWORK');const {loadAMap}=await import('./amap')
 const first=loadAMap(),rejected=expect(first).rejects.toThrow('脚本加载失败');const script=document.querySelector('script[data-amap-loader]');script.dispatchEvent(new Event('error'));await rejected;expect(document.querySelector('script[data-amap-loader]')).toBeNull()
 const second=loadAMap();expect(second).not.toBe(first);window.AMap={Map:class{}};document.querySelector('script[data-amap-loader]').dispatchEvent(new Event('load'));expect(await second).toBe(window.AMap)
})
it('map timeout exits loading and permits fresh retry, with no SDK/region network executed in jsdom',async()=>{
 vi.resetModules();vi.stubEnv('VUE_APP_AMAP_KEY','SYNTHETIC_LOCAL_NO_NETWORK');vi.useFakeTimers();const {loadAMap}=await import('./amap');const rejected=expect(loadAMap()).rejects.toThrow('加载超时');await vi.advanceTimersByTimeAsync(15001);await rejected;expect(document.querySelector('script[data-amap-loader]')).toBeNull()
 const second=loadAMap();window.AMap={Map:class{}};document.querySelector('script[data-amap-loader]').dispatchEvent(new Event('load'));expect(await second).toBe(window.AMap)
})
