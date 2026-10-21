import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import Activities from './ActivitiesPage.vue'
import Professional from './SkateProfessionalPage.vue'
import Community from './CommunityPage.vue'
import Publish from './PostPublishPage.vue'
import Place from './PlaceDetailPage.vue'
import Profile from './UserProfilePage.vue'
const m=vi.hoisted(()=>({api:Object.fromEntries(['publicActivities','places','skateTricks','videos','skateTrainingPlans','skateTrainingPlanDetail','skateEquipments','equipmentCategories','postCategories','posts','notices','news','placeDetail','placeReviews','publicUserProfile','publicUserPosts'].map(k=>[k,vi.fn()])),regions:vi.fn(),amap:vi.fn(),push:vi.fn()}))
vi.mock('../api',()=>({api:m.api}))
vi.mock('../utils/regions',()=>({regionOptions:m.regions}))
vi.mock('../utils/amap',()=>({loadAMap:m.amap,amapKeyTail:()=>''}))
vi.mock('../utils/requestCache',()=>({getCachedResource:(_key,load)=>load(),loadCachedPublic:(_key,load)=>load()}))
vi.mock('vue-router',()=>({useRouter:()=>({push:m.push}),useRoute:()=>({params:{id:3}})}))
let w
const mountPage=C=>{w=mount(C,{global:{stubs:{RouterLink:true,AppIcon:true,SkateboardSketch:true,AiLoadingOverlay:true}}});return w}
const button=text=>w.findAll('button').find(b=>b.text()===text)
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
beforeEach(()=>{
 vi.resetAllMocks();localStorage.clear();sessionStorage.clear();vi.stubGlobal('requestAnimationFrame',fn=>fn())
 for(const k of ['places','skateTricks','videos','notices','news'])m.api[k].mockResolvedValue([])
 m.api.publicActivities.mockResolvedValue({list:[]});m.api.posts.mockResolvedValue({list:[],total:0});m.api.skateEquipments.mockResolvedValue({list:[],total:60})
 m.api.postCategories.mockResolvedValue({filterOptions:[{key:'装备讨论',label:'装备讨论'},{key:'装备分享',label:'装备分享'},{key:'历史第25类',label:'历史第25类'}],createOptions:[{key:'技巧交流',label:'技巧交流'}],aliasMap:{}})
 m.api.equipmentCategories.mockResolvedValue([{key:'工具',label:'工具'},{key:'砂纸',label:'砂纸'}])
 m.api.skateTrainingPlans.mockResolvedValue([{planId:1,planName:'真实形状7天',durationDays:7}]);m.api.skateTrainingPlanDetail.mockResolvedValue({plan:{planId:1,planName:'真实形状7天'},tasks:[1,2,3].map(n=>({taskId:n,dayNo:n,taskTitle:`实际任务${n}`})),scheduleCoverage:{declaredDays:7,availableDays:[1,2,3],missingDays:[4,5,6,7],complete:false}})
 m.api.placeDetail.mockResolvedValue({placeId:3,name:'合成平地',score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'},obstacleTags:['rail','unknown-tag'],safetyTips:['Check lighting before night sessions.']});m.api.placeReviews.mockResolvedValue([])
 m.api.publicUserProfile.mockResolvedValue({userId:3,username:'合成用户',followerCount:0,avatar:'https://example.invalid/avatar'});m.api.publicUserPosts.mockResolvedValue({list:[],total:0})
 m.regions.mockImplementation(async level=>level==='province'?['四川省','湖北省']:level==='city'?['成都市']:['郫都区','武侯区']);m.amap.mockRejectedValue(new Error('SYNTHETIC_MAP_BLOCKED'))
})
afterEach(()=>{w?.unmount();vi.unstubAllGlobals()})
it('F01 SDK denied leaves regions usable, retries region alone and never initializes map',async()=>{
 m.regions.mockRejectedValueOnce(new Error('SYNTHETIC_REGION_TIMEOUT'));mountPage(Activities);await flushPromises();expect(w.text()).toContain('SYNTHETIC_REGION_TIMEOUT');expect(w.find('select').attributes('disabled')).toBeDefined()
 await button('重试省份资料').trigger('click');await flushPromises();expect(w.find('select').attributes('disabled')).toBeUndefined();expect(m.amap).not.toHaveBeenCalled()
 await button('加载地图').trigger('click');await flushPromises();expect(w.text()).toContain('SYNTHETIC_MAP_BLOCKED');expect(w.find('select').attributes('disabled')).toBeUndefined()
 await w.find('select').setValue('四川省');await flushPromises();expect(w.findAll('select')[1].text()).toContain('成都市')
})
it('F01 rejects stale city success and error after parent change, clearing children immediately',async()=>{
 mountPage(Activities);await flushPromises();const old=deferred(),fresh=deferred()
 m.regions.mockImplementation(level=>level==='city'?old.promise:Promise.resolve([]))
 await w.findAll('select')[0].setValue('四川省');expect(w.findAll('select')[1].element.value).toBe('')
 m.regions.mockImplementation(()=>fresh.promise);await w.findAll('select')[0].setValue('湖北省');fresh.resolve(['武汉市']);await flushPromises();old.resolve(['成都市']);await flushPromises();expect(w.findAll('select')[1].text()).toContain('武汉市');expect(w.findAll('select')[1].text()).not.toContain('成都市')
 const late=deferred();m.regions.mockImplementation(()=>late.promise);await w.findAll('select')[0].setValue('四川省');await button('重置').trigger('click');late.reject(new Error('LATE_OLD_ERROR'));await flushPromises();expect(w.text()).not.toContain('LATE_OLD_ERROR');expect(w.findAll('select')[1].findAll('option')).toHaveLength(1)
})
it('F01 guards district responses across city change and preserves exact city/district query semantics',async()=>{
 m.regions.mockImplementation(async level=>level==='province'?['四川省']:level==='city'?['成都市','广元市']:['郫都区'])
 mountPage(Activities);await flushPromises();await w.findAll('select')[0].setValue('四川省');await flushPromises();const old=deferred();m.regions.mockImplementationOnce(()=>old.promise)
 await w.findAll('select')[1].setValue('成都市');m.regions.mockResolvedValueOnce([]);await w.findAll('select')[1].setValue('广元市');await flushPromises();old.resolve(['郫都区']);await flushPromises();expect(w.findAll('select')[2].text()).not.toContain('郫都区')
 m.regions.mockResolvedValueOnce(['郫都区']);await w.findAll('select')[1].setValue('成都市');await flushPromises();await w.findAll('select')[2].setValue('郫都区');await button('筛选').trigger('click');await flushPromises()
 expect(m.api.publicActivities).toHaveBeenLastCalledWith(expect.objectContaining({city:'成都市',district:'郫都区',expired:false}));expect(m.api.publicActivities.mock.lastCall[0]).not.toHaveProperty('province')
})
it('F04 activity list survives place failure with retry, then renders unavailable versus real empty summaries',async()=>{
 m.api.places.mockRejectedValueOnce(new Error('SUMMARY_FAILED'));mountPage(Activities);await flushPromises();expect(w.text()).toContain('SUMMARY_FAILED');expect(w.text()).not.toContain('当前暂无推荐场地')
 m.api.places.mockResolvedValue([{placeId:1,name:'真实空摘要',score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'}},{placeId:2,name:'缺失摘要',score:5,reviewCount:999}]);await button('重试场地').trigger('click');await flushPromises()
 expect(w.text()).toContain('暂无本站评分');expect(w.text()).toContain('评分资料暂不可用');expect(w.text()).not.toContain('203');expect(w.text()).not.toContain('4.9')
})
it('F03 discloses 7/3 actual tasks and missing days, not a login unlock',async()=>{
 mountPage(Professional);await flushPromises();expect(w.text()).toContain('提交动作打卡需要登录');await button('训练计划').trigger('click');await flushPromises();expect(w.text()).toContain('计划7天，已有3天任务，剩余资料待补充');expect(w.text()).toContain('4、5、6、7');expect(w.findAll('.task-row')).toHaveLength(3);expect(w.text()).toContain('登录不会补齐')
})
it('F03 complete/unknown/empty coverage and retry remain distinct',async()=>{
 m.api.skateTrainingPlanDetail.mockRejectedValueOnce(new Error('PLAN_FAILED'));mountPage(Professional);await flushPromises();await button('训练计划').trigger('click');await flushPromises();expect(w.text()).toContain('PLAN_FAILED')
 m.api.skateTrainingPlanDetail.mockResolvedValueOnce({plan:{planId:1},tasks:[{taskId:1,dayNo:1},{taskId:2,dayNo:2}],scheduleCoverage:{declaredDays:2,availableDays:[1,2],missingDays:[],complete:true}});await button('重试训练详情').trigger('click');await flushPromises();expect(w.text()).toContain('任务资料完整覆盖');expect(w.findAll('.task-row')).toHaveLength(2)
 w.unmount();m.api.skateTrainingPlanDetail.mockResolvedValue({plan:{planId:1},tasks:[]});mountPage(Professional);await flushPromises();await button('训练计划').trigger('click');await flushPromises();expect(w.text()).toContain('任务覆盖资料暂不可确认');expect(w.text()).not.toContain('任务资料完整覆盖')
})
it('F05 community uses full options outside current empty page, exact historical keys and retry',async()=>{
 m.api.postCategories.mockRejectedValueOnce(new Error('CATEGORIES_FAILED'));mountPage(Community);await flushPromises();expect(w.text()).toContain('CATEGORIES_FAILED');await button('重试分类').trigger('click');await flushPromises();await button('历史第25类').trigger('click');await flushPromises();expect(m.api.posts).toHaveBeenLastCalledWith(expect.objectContaining({category:'历史第25类'}));expect(w.text()).toContain('装备讨论');expect(w.text()).toContain('装备分享');await button('全部').trigger('click');await flushPromises();expect(m.api.posts).toHaveBeenLastCalledWith(expect.objectContaining({category:''}))
})
it('F05 create suggestions share API while free historical input stays possible',async()=>{
 mountPage(Publish);await flushPromises();expect(w.find('datalist').text()).toContain('技巧交流');expect(w.find('datalist').text()).not.toContain('历史第25类');const category=w.find('input[list]');await category.setValue('历史自由值');expect(category.element.value).toBe('历史自由值')
})
it('F06 full category endpoint supplies tools/grip across pagination and clear uses empty query',async()=>{
 mountPage(Professional);await flushPromises();await button('装备库').trigger('click');await flushPromises();expect(w.find('select[aria-label="装备分类"]').text()).toContain('工具');expect(w.find('select[aria-label="装备分类"]').text()).toContain('砂纸');await w.find('select[aria-label="装备分类"]').setValue('工具');await button('筛选装备').trigger('click');await flushPromises();expect(m.api.skateEquipments).toHaveBeenLastCalledWith({page:1,size:30,category:'工具'});await w.find('select[aria-label="装备分类"]').setValue('');await button('筛选装备').trigger('click');await flushPromises();expect(m.api.skateEquipments).toHaveBeenLastCalledWith({page:1,size:30})
})
it('F06 categories error is independent and retry can yield real empty options',async()=>{
 m.api.equipmentCategories.mockRejectedValueOnce(new Error('EQUIPMENT_CATEGORIES_FAILED'));mountPage(Professional);await flushPromises();await button('装备库').trigger('click');await flushPromises();expect(w.text()).toContain('EQUIPMENT_CATEGORIES_FAILED');m.api.equipmentCategories.mockResolvedValueOnce([]);await button('重试装备分类').trigger('click');await flushPromises();expect(w.text()).not.toContain('EQUIPMENT_CATEGORIES_FAILED');expect(w.find('select[aria-label="装备分类"]').findAll('option')).toHaveLength(1)
})
it('F04 detail shows local empty not legacy numbers; review failure never says zero reviews and is retryable',async()=>{
 m.api.placeReviews.mockRejectedValueOnce(new Error('REVIEWS_FAILED'));mountPage(Place);await flushPromises();expect(w.text()).toContain('暂无本站评分');expect(w.text()).not.toContain('203');expect(w.text()).not.toContain('4.9');expect(w.text()).toContain('REVIEWS_FAILED');expect(w.text()).not.toContain('还没有评价')
 await button('重试评价').trigger('click');await flushPromises();expect(w.text()).toContain('还没有评价');expect(w.text()).toContain('栏杆');expect(w.text()).toContain('unknown-tag');expect(w.text()).toContain('夜滑前检查照明条件');expect(w.text()).not.toContain('先收藏场地')
})
it.each([0,1,12])('F07 true fan count %i never uses self-avatar as fans',async count=>{
 m.api.publicUserProfile.mockResolvedValue({userId:3,username:'合成用户',followerCount:count,avatar:'https://example.invalid/avatar'});mountPage(Profile);await flushPromises();const rail=w.find('.profile-rail');expect(rail.findAll('img')).toHaveLength(0);expect(rail.text()).toContain(count===0?'暂无粉丝':`${count} 位滑手关注 TA`)
 const image=w.find('.profile-avatar');await image.trigger('error');expect(image.attributes('src')).toBe('/fallback/avatar.svg');await image.trigger('error');expect(image.element.hidden).toBe(true)
})
