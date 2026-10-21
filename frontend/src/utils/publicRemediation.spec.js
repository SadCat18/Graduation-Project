import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { regionOptions } from './regions'
import regions from '../../public/regions/china-area-5.0.1.json'
import { localReviewSummary, reviewScoreText, reviewCountText } from './localReviews'
import { buildActivityPlaceRecommendations } from './activityPlaceRecommendations'
import { absoluteDate, bulletinTiming, placeTagLabel, sourceLinkLabel } from './publicDisplay'
import { localImageFallback } from './imageFallback'
import { api } from '../api'
import http from '../api/http'
const response = data => ({ok:true,json:async()=>data})
afterEach(()=>{vi.unstubAllGlobals();vi.useRealTimers()})
describe('pinned region snapshot: actual names and independent failure semantics',()=>{
 beforeEach(()=>vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response(regions))))
 it('covers municipalities including Chongqing county nodes without submitting administrative group names',async()=>{
  expect(await regionOptions('province')).toHaveLength(34)
  for(const p of ['北京市','天津市','上海市','重庆市']) expect(await regionOptions('city',p)).toEqual([p])
  const districts=await regionOptions('district','重庆市','重庆市')
  expect(districts).toContain('渝中区');expect(districts).toContain('巫山县');expect(districts).not.toContain('市辖区')
 })
 it('promotes actual province-direct county cities, retaining real empty lower levels',async()=>{
  expect(await regionOptions('city','河南省')).toContain('济源市')
  expect(await regionOptions('city','湖北省')).toContain('仙桃市')
  expect(await regionOptions('city','新疆维吾尔自治区')).toContain('石河子市')
  expect(await regionOptions('city','湖北省')).not.toContain('省直辖县级行政区划')
  expect(await regionOptions('district','湖北省','仙桃市')).toEqual([])
 })
 it('retains Chengdu/Pidu/Wuhou names used by observed real public activities (not candidate HTTP acceptance)',async()=>{
  expect(await regionOptions('city','四川省')).toContain('成都市')
  const districts=await regionOptions('district','四川省','成都市')
  expect(districts).toEqual(expect.arrayContaining(['郫都区','武侯区']));expect(districts).not.toContain('市辖区')
 })
 it('handles direct SAR subdivisions without inventing cities or translating raw keys',async()=>{
  expect(await regionOptions('city','香港特别行政区')).toEqual(['香港特别行政区'])
  expect(await regionOptions('district','香港特别行政区','香港特别行政区')).toContain('中西區')
 })
 it('rejects HTTP failure, invalid shape and mismatched parent instead of returning empty',async()=>{
  fetch.mockResolvedValueOnce({ok:false});await expect(regionOptions('province')).rejects.toThrow('加载失败')
  fetch.mockResolvedValueOnce(response({}));await expect(regionOptions('province')).rejects.toThrow('格式')
  await expect(regionOptions('district','四川省','北京市')).rejects.toThrow('城市资料不可用')
 })
 it('times out independently of SDK and can retry',async()=>{
  vi.useFakeTimers();fetch.mockImplementationOnce(()=>new Promise(()=>{}))
  const rejected=expect(regionOptions('province')).rejects.toThrow('超时');await vi.advanceTimersByTimeAsync(10001);await rejected
  expect(await regionOptions('province')).toContain('四川省')
 })
})
describe('public contract truthfulness',()=>{
 it('uses null/0 as empty, valid numeric 0 as a score, invalid/missing as unavailable',()=>{
  const empty={score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'}}
  expect(reviewScoreText(empty)).toBe('暂无本站评分');expect(reviewCountText(empty)).toBe('0 条本站评价')
  expect(reviewScoreText({...empty,localReviewSummary:{score:0,count:1,source:'LOCAL_REVIEWS'}})).toBe('0.0')
  for(const s of [undefined,{score:null,count:2,source:'LOCAL_REVIEWS'},{score:'4.9',count:2,source:'LOCAL_REVIEWS'},{score:4.9,count:0,source:'LOCAL_REVIEWS'}]){
   expect(localReviewSummary({...empty,localReviewSummary:s})).toBeNull();expect(reviewScoreText({...empty,localReviewSummary:s})).toBe('评分资料暂不可用')
  }
 })
 it('ranking, heat and tags ignore legacy 4.9/203 and put unrated after rated',()=>{
  const places=[{placeId:1,score:4.9,reviewCount:203,localReviewSummary:{score:null,count:0,source:'LOCAL_REVIEWS'}},{placeId:2,score:0,reviewCount:0,localReviewSummary:{score:4.5,count:1,source:'LOCAL_REVIEWS'}},{placeId:3,score:5,reviewCount:999}]
  const ranked=buildActivityPlaceRecommendations(places,[]);expect(ranked.map(p=>p.placeId)).toEqual([2,1,3])
  expect(ranked[1].placeTags).not.toContain('高评分');expect(ranked[2].placeTags).not.toContain('人气场地')
 })
 it('rejects HTTP200/code500 with omitted data for every new/affected read endpoint',async()=>{
  const old=http.defaults.adapter;http.defaults.adapter=async config=>({status:200,data:{code:500,message:'SYNTHETIC_AGGREGATE_FAILED'},headers:{},config})
  try{for(const call of [()=>api.postCategories(),()=>api.equipmentCategories(),()=>api.places(),()=>api.placeDetail(3),()=>api.placeReviews(3),()=>api.skateTrainingPlanDetail(1)])await expect(call()).rejects.toThrow('SYNTHETIC_AGGREGATE_FAILED')}finally{http.defaults.adapter=old}
 })
 it('preserves successful empty contracts separately from failures',async()=>{
  const old=http.defaults.adapter;http.defaults.adapter=async config=>({status:200,data:{code:200,data:config.url.includes('posts')?{filterOptions:[],createOptions:[],aliasMap:{}}:[]},headers:{},config})
  try{expect(await api.equipmentCategories()).toEqual([]);expect(await api.postCategories()).toEqual({filterOptions:[],createOptions:[],aliasMap:{}})}finally{http.defaults.adapter=old}
 })
 it('fallback changes image once then hides, including initial local image failure',()=>{
  const img=document.createElement('img');img.src='https://example.invalid/avatar';localImageFallback({currentTarget:img},'/fallback/avatar.svg');expect(img.getAttribute('src')).toBe('/fallback/avatar.svg');localImageFallback({currentTarget:img},'/fallback/avatar.svg');expect(img.hidden).toBe(true)
  const local=document.createElement('img');local.src='/fallback/avatar.svg';localImageFallback({currentTarget:local},'/fallback/avatar.svg');expect(local.hidden).toBe(true)
 })
 it('labels actual URL roles and maps known display tags without changing unknown keys',()=>{
  expect(sourceLinkLabel('https://www.dobyheart.com/')).toBe('站点首页');expect(sourceLinkLabel('https://s.taobao.com/search?q=board')).toBe('参考平台');expect(sourceLinkLabel('https://item.taobao.com/item.htm?id=1')).toBe('商品来源');expect(sourceLinkLabel('javascript:alert(1)')).toBe('')
  expect(placeTagLabel('ledge')).toBe('台沿');expect(placeTagLabel('Check lighting before night sessions.')).toBe('夜滑前检查照明条件。');expect(placeTagLabel('future-tag')).toBe('future-tag')
  expect(absoluteDate('2026-05-01T12:00:00')).toBe('2026-05-01');expect(bulletinTiming({createTime:'2026-05-01',content:'本周六活动'},Date.parse('2026-10-21'))).toContain('历史活动通知');expect(bulletinTiming({createTime:'2026-05-01',content:'资料'})).toBe('时效待确认')
 })
})
