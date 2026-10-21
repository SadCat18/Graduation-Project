const {test,expect}=require('@playwright/test')
// Deterministic local browser response ordering; no production traffic or business writes.
const rows=(name,count,category)=>Array.from({length:count},(_,i)=>({postId:name+i,title:`SYNTHETIC ${name}${i}`,category,content:'local race control',username:'test'}))
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r});return {promise,resolve}}
async function setup(page,handler){
 const errors=[],completed=[],sent=[]
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())})
 page.on('requestfailed',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))errors.push(`API transport failed ${new URL(r.url()).pathname}`)})
 page.on('response',r=>{const u=new URL(r.url());if(u.pathname==='/api/public/posts')completed.push({category:u.searchParams.get('category'),status:r.status()});if(u.pathname.startsWith('/api/')&&r.status()!==200)errors.push(`API HTTP ${r.status()}`)})
 await page.route('**/*',async route=>{
  const u=new URL(route.request().url())
  if(!['127.0.0.1','localhost'].includes(u.hostname))return route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"/>'})
  if(!u.pathname.startsWith('/api/'))return route.continue()
  expect(route.request().method()).toBe('GET')
  let data=[]
  if(u.pathname==='/api/public/posts/categories')data={filterOptions:[{key:'装备讨论',label:'装备讨论'},{key:'技巧交流',label:'技巧交流'}],createOptions:[],aliasMap:{}}
  if(u.pathname==='/api/public/posts'){sent.push(Object.fromEntries(u.searchParams));data=await handler(u)}
  return route.fulfill({json:{code:200,data,message:'SYNTHETIC_LOCAL_ONLY'}})
 })
 return {errors,completed,sent}
}
test('new exact category completes before initial all; old 6 rows never replace current 2',async({page})=>{
 const old=deferred();let count=0
 const observed=await setup(page,async u=>{count++;if(count===1){await old.promise;return {list:rows('OLD',6,'技巧交流'),total:25}}expect(u.searchParams.get('category')).toBe('装备讨论');return {list:rows('CURRENT',2,'装备讨论'),total:2}})
 try{
  await page.goto('/community');await page.getByRole('button',{name:'装备讨论',exact:true}).click();await expect(page.locator('.post-card-item')).toHaveCount(2);await expect(page.getByText('装备讨论 · 最新排序 · 共 2 条')).toBeVisible();expect(observed.completed).toEqual([{category:'装备讨论',status:200}]);old.resolve();await expect.poll(()=>observed.completed.length).toBe(2)
  await expect(page.locator('.post-card-item')).toHaveCount(2);await expect(page.locator('.post-card-item').first()).toContainText('CURRENT0');await expect(page.getByText('装备讨论 · 最新排序 · 共 2 条')).toBeVisible();expect(observed.sent.map(x=>x.category)).toEqual(['','装备讨论']);expect(observed.errors).toEqual([])
 }finally{old.resolve()}
})
test('A to B to A still rejects old same-query A in actual browser',async({page})=>{
 const old=deferred();let count=0
 const observed=await setup(page,async u=>{count++;if(count===1){await old.promise;return {list:rows('FIRST-A',6,'技巧交流'),total:25}}return u.searchParams.get('category')?{list:rows('B',2,'装备讨论'),total:2}:{list:rows('LATEST-A',1,'技巧交流'),total:1}})
 try{
  await page.goto('/community');await page.getByRole('button',{name:'装备讨论',exact:true}).click();await expect(page.locator('.post-card-item')).toHaveCount(2);await page.getByRole('button',{name:'全部',exact:true}).click();await expect(page.locator('.post-card-item')).toHaveCount(1);await expect(page.locator('.post-card-item')).toContainText('LATEST-A0');old.resolve();await expect.poll(()=>observed.completed.length).toBe(3);await expect(page.locator('.post-card-item')).toHaveCount(1);await expect(page.locator('.post-card-item')).toContainText('LATEST-A0');await expect(page.getByText('全部分类 · 最新排序 · 共 1 条')).toBeVisible();expect(observed.errors).toEqual([])
 }finally{old.resolve()}
})
