import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import Community from './CommunityPage.vue'
import { clearAuth, setAuth } from '../utils/auth'
const m=vi.hoisted(()=>({api:{posts:vi.fn(),recommendations:vi.fn(),postCategories:vi.fn(),notices:vi.fn(),news:vi.fn()}}))
vi.mock('../api',()=>({api:m.api}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()}),useRoute:()=>({path:'/community',query:{}})}))
vi.mock('../utils/requestCache',()=>({loadCachedPublic:(_key,load)=>load()}))
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
const rows=(prefix,count=2,category='装备讨论')=>Array.from({length:count},(_,i)=>({postId:prefix+i,title:`${prefix}${i}`,category,content:'合成竞态回归',username:'测试'}))
let w
const button=text=>w.findAll('button').find(b=>b.text()===text)
const cards=()=>w.findAll('.post-card-item').map(x=>x.text())
const mountPage=()=>{w=mount(Community,{global:{stubs:{RouterLink:true,AppIcon:true,SkateboardSketch:true}}});return w}
beforeEach(()=>{
 vi.resetAllMocks();localStorage.clear();sessionStorage.clear()
 m.api.posts.mockResolvedValue({list:rows('INITIAL'),total:18});m.api.recommendations.mockResolvedValue({posts:rows('REC')})
 m.api.postCategories.mockResolvedValue({filterOptions:['装备讨论','技巧交流'].map(key=>({key,label:key})),createOptions:[],aliasMap:{}})
 m.api.notices.mockResolvedValue([]);m.api.news.mockResolvedValue([])
})
afterEach(()=>w?.unmount())
it('initial all slow, new exact category fast: late old success keeps current 2 rows/total/filter',async()=>{
 const old=deferred();m.api.posts.mockImplementationOnce(()=>old.promise).mockResolvedValueOnce({list:rows('CURRENT'),total:2});mountPage();await flushPromises();await button('装备讨论').trigger('click');await flushPromises();expect(w.text()).toContain('装备讨论 · 最新排序 · 共 2 条');
 old.resolve({list:rows('OLD',6,'技巧交流'),total:25});await flushPromises();expect(w.text()).toContain('装备讨论 · 最新排序 · 共 2 条');expect(w.findAll('.post-card-item')).toHaveLength(2);expect(w.text()).toContain('CURRENT0');expect(w.text()).not.toContain('OLD0');expect(m.api.posts.mock.calls[1][0].category).toBe('装备讨论')
})
it('A to B to A rejects first A even when its query strings match the latest A',async()=>{
 const first=deferred(),middle=deferred();m.api.posts.mockImplementationOnce(()=>first.promise).mockImplementationOnce(()=>middle.promise).mockResolvedValueOnce({list:rows('LATEST-A'),total:2});mountPage();await flushPromises();await button('装备讨论').trigger('click');await button('全部').trigger('click');await flushPromises();first.resolve({list:rows('FIRST-A',6),total:25});middle.resolve({list:rows('B'),total:9});await flushPromises();expect(w.text()).toContain('LATEST-A0');expect(w.text()).not.toContain('FIRST-A0');expect(w.text()).not.toContain('B0');expect(w.text()).toContain('全部分类 · 最新排序 · 共 2 条')
})
it.each(['success','failure'])('old %s/finally cannot clear newer loading or set error',async kind=>{
 const old=deferred(),fresh=deferred();m.api.posts.mockImplementationOnce(()=>old.promise).mockImplementationOnce(()=>fresh.promise);mountPage();await flushPromises();await button('装备讨论').trigger('click');if(kind==='failure')old.reject(new Error('OLD_ERROR'));else old.resolve({list:rows('OLD'),total:1});await flushPromises();expect(w.text()).toContain('正在滑向最新帖子');expect(w.text()).not.toContain('OLD_ERROR');fresh.resolve({list:rows('FRESH'),total:2});await flushPromises();expect(w.text()).not.toContain('正在滑向最新帖子');expect(w.text()).toContain('FRESH0')
})
it.each(['success','failure'])('late old %s cannot erase a current error; refresh retries current exact query',async kind=>{
 const old=deferred();m.api.posts.mockImplementationOnce(()=>old.promise).mockRejectedValueOnce(new Error('CURRENT_ERROR'));mountPage();await flushPromises();await button('装备讨论').trigger('click');await flushPromises();if(kind==='success')old.resolve({list:rows('OLD'),total:100});else old.reject(new Error('OLD_ERROR'));await flushPromises();expect(w.text()).toContain('CURRENT_ERROR');expect(w.text()).not.toContain('OLD0');m.api.posts.mockResolvedValueOnce({list:rows('RETRY'),total:2});await button('刷新').trigger('click');await flushPromises();expect(w.text()).not.toContain('CURRENT_ERROR');expect(w.text()).toContain('RETRY0');expect(m.api.posts.mock.lastCall[0]).toEqual({page:1,size:6,category:'装备讨论',keyword:'',sort:'latest'})
})
it('old page total cannot roll back the new category page or its pending loading',async()=>{
 mountPage();await flushPromises();const old=deferred(),fresh=deferred();m.api.posts.mockImplementationOnce(()=>old.promise);await button('下一页').trigger('click');await button('装备讨论').trigger('click');await flushPromises();m.api.posts.mockImplementationOnce(()=>fresh.promise);await button('下一页').trigger('click');old.resolve({list:[],total:0});await flushPromises();expect(w.text()).toContain('第 2 / 3 页');expect(w.text()).toContain('正在滑向最新帖子');expect(m.api.posts).toHaveBeenCalledTimes(4);fresh.resolve({list:rows('NEW-PAGE2'),total:18});await flushPromises();expect(w.text()).toContain('NEW-PAGE20');expect(w.text()).toContain('第 2 / 3 页')
})
it('current out-of-range page still reloads last valid page and old finally preserves new loading',async()=>{
 mountPage();await flushPromises();const reload=deferred();m.api.posts.mockResolvedValueOnce({list:[],total:1}).mockImplementationOnce(()=>reload.promise);await button('下一页').trigger('click');await flushPromises();expect(w.text()).toContain('第 1 / 1 页');expect(w.text()).toContain('正在滑向最新帖子');expect(m.api.posts.mock.lastCall[0].page).toBe(1);reload.resolve({list:rows('VALID',1),total:1});await flushPromises();expect(w.text()).toContain('VALID0');expect(w.text()).not.toContain('正在滑向最新帖子')
})
it('keyword and sort are immutable per read; later search response wins',async()=>{
 const old=deferred();m.api.posts.mockImplementationOnce(()=>old.promise);mountPage();await flushPromises();await w.find('input[placeholder="搜索标题、正文、路线或装备..."]').setValue('new keyword');m.api.posts.mockResolvedValueOnce({list:rows('NEW'),total:2});await button('搜索').trigger('click');await flushPromises();await button('热门').trigger('click');await flushPromises();old.resolve({list:rows('OLD'),total:100});await flushPromises();expect(m.api.posts.mock.lastCall[0]).toMatchObject({keyword:'new keyword',sort:'hot',page:1});expect(w.text()).not.toContain('OLD0');expect(w.text()).toContain('热门排序')
})
it('normal and recommendation modes share the generation; old normal cannot overwrite recommendation',async()=>{
 setAuth('SYNTHETIC_TOKEN','USER','test');const old=deferred();m.api.posts.mockImplementationOnce(()=>old.promise);mountPage();await flushPromises();await button('推荐').trigger('click');await flushPromises();old.resolve({list:rows('OLD'),total:100});await flushPromises();expect(w.text()).toContain('REC0');expect(w.text()).not.toContain('OLD0');expect(w.text()).toContain('推荐排序 · 共 2 条')
})
it('recommended filtering uses captured keyword/page and late recommendation cannot replace normal mode',async()=>{
 setAuth('SYNTHETIC_TOKEN','USER','test');mountPage();await flushPromises();const rec=deferred();m.api.recommendations.mockImplementationOnce(()=>rec.promise);await w.find('input[placeholder="搜索标题、正文、路线或装备..."]').setValue('alpha');await button('推荐').trigger('click');await w.find('input[placeholder="搜索标题、正文、路线或装备..."]').setValue('beta');rec.resolve({posts:[...rows('alpha'),...rows('beta')]});await flushPromises();expect(w.text()).toContain('alpha0');expect(w.text()).not.toContain('beta0')
 const late=deferred();m.api.recommendations.mockImplementationOnce(()=>late.promise);await button('搜索').trigger('click');await button('最新').trigger('click');await flushPromises();late.resolve({posts:rows('LATE-REC')});await flushPromises();expect(w.text()).toContain('INITIAL0');expect(w.text()).not.toContain('LATE-REC0')
})
it('anonymous recommended mode retains hot API fallback, identity event invalidates pending read',async()=>{
 const old=deferred();mountPage();await flushPromises();m.api.posts.mockImplementationOnce(()=>old.promise);await button('推荐').trigger('click');expect(m.api.posts.mock.lastCall[0].sort).toBe('hot');setAuth('NEW_SYNTHETIC_TOKEN','USER','test');old.resolve({list:rows('OLD-ANON'),total:100});await flushPromises();expect(w.text()).not.toContain('OLD-ANON0');expect(w.text()).not.toContain('正在滑向最新帖子');await button('刷新').trigger('click');await flushPromises();expect(m.api.recommendations).toHaveBeenCalledTimes(1);expect(w.text()).toContain('REC0')
})
it.each(['success','failure'])('unmount invalidates late %s and removes auth listener',async kind=>{
 const pending=deferred();m.api.posts.mockImplementationOnce(()=>pending.promise);const remove=vi.spyOn(window,'removeEventListener');mountPage();await flushPromises();const state=w.vm.$.setupState;w.unmount();const before={posts:state.posts,total:state.total,error:state.error,loading:state.loading};if(kind==='success')pending.resolve({list:rows('LATE'),total:100});else pending.reject(new Error('LATE_ERROR'));await flushPromises();expect({posts:state.posts,total:state.total,error:state.error,loading:state.loading}).toEqual(before);expect(remove.mock.calls.some(([event])=>event==='auth-changed')).toBe(true);remove.mockRestore();w=null
})

it('recommended current error stays visible and same-filter refresh retries without a false empty success',async()=>{
 setAuth('SYNTHETIC_TOKEN','USER','test');mountPage();await flushPromises();await button('装备讨论').trigger('click');await flushPromises();m.api.recommendations.mockRejectedValueOnce(new Error('REC_CURRENT_ERROR'));await button('推荐').trigger('click');await flushPromises();expect(w.text()).toContain('REC_CURRENT_ERROR');m.api.recommendations.mockResolvedValueOnce({posts:[...rows('MATCH',2,'装备讨论'),...rows('OTHER',3,'技巧交流')]});await button('刷新').trigger('click');await flushPromises();expect(w.text()).not.toContain('REC_CURRENT_ERROR');expect(w.text()).toContain('MATCH0');expect(w.text()).not.toContain('OTHER0');expect(w.text()).toContain('装备讨论 · 推荐排序 · 共 2 条')
})
it('old recommended failure/finally cannot replace normal current loading/error',async()=>{
 setAuth('SYNTHETIC_TOKEN','USER','test');mountPage();await flushPromises();const rec=deferred(),normal=deferred();m.api.recommendations.mockImplementationOnce(()=>rec.promise);await button('推荐').trigger('click');m.api.posts.mockImplementationOnce(()=>normal.promise);await button('热门').trigger('click');rec.reject(new Error('OLD_REC_ERROR'));await flushPromises();expect(w.text()).toContain('正在滑向最新帖子');expect(w.text()).not.toContain('OLD_REC_ERROR');normal.resolve({list:rows('HOT'),total:2});await flushPromises();expect(w.text()).toContain('HOT0')
})
