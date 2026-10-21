const {test,expect}=require('@playwright/test')
const fs=require('node:fs'),path=require('node:path')
test('actual H2 service filtered 2 completes before initial 6; late real response cannot replace current state',async({page})=>{
 const category=process.env.COMMUNITY_REVIEW_CATEGORY,errors=[],events=[],reads=[]
 let releaseOld,oldFetched;const gate=new Promise(r=>{releaseOld=r}),captured=new Promise(r=>{oldFetched=r})
 page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())})
 page.on('requestfailed',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))errors.push('API transport '+r.failure()?.errorText)})
 await page.route('**/*',route=>{const u=new URL(route.request().url());return ['127.0.0.1','localhost'].includes(u.hostname)?route.continue():route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"/>'})})
 let ordinal=0
 await page.route('**/api/public/posts?**',async route=>{
  const u=new URL(route.request().url());expect(route.request().method()).toBe('GET');const n=++ordinal
  const upstream=await route.fetch(),body=await upstream.json();expect(upstream.status()).toBe(200);expect(body.code).toBe(200)
  events.push({phase:'real upstream completed',ordinal:n,query:Object.fromEntries(u.searchParams),rows:body.data.list.length,total:body.data.total})
  if(n===1){expect(u.searchParams.get('category')).toBe('');expect(body.data.list).toHaveLength(6);oldFetched();await gate}
  else{expect(u.searchParams.get('category')).toBe(category);expect(body.data.total).toBe(2);expect(body.data.list).toHaveLength(2)}
  await route.fulfill({response:upstream});events.push({phase:'delivered unchanged real response',ordinal:n})
 })
 page.on('response',r=>{if(new URL(r.url()).pathname==='/api/public/posts')reads.push(r.finished())})
 try{
  await page.goto('/community');await captured
  await page.getByRole('button',{name:category,exact:true}).click()
  await expect(page.locator('.post-card-item')).toHaveCount(2);await expect(page.getByText(category+' · 最新排序 · 共 2 条')).toBeVisible()
  expect(events.filter(e=>e.phase==='delivered unchanged real response').map(e=>e.ordinal)).toEqual([2])
  const before=await page.locator('.post-card-item').allTextContents();releaseOld()
  await expect.poll(()=>events.filter(e=>e.phase==='delivered unchanged real response').length).toBe(2);await Promise.all(reads);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))))
  await expect(page.locator('.post-card-item')).toHaveCount(2);await expect(page.getByText(category+' · 最新排序 · 共 2 条')).toBeVisible();expect(await page.locator('.post-card-item').allTextContents()).toEqual(before)
  expect(errors).toEqual([])
 }finally{
  releaseOld();fs.writeFileSync(path.join(process.env.COMMUNITY_REVIEW_EVIDENCE,'browser-order.json'),JSON.stringify({layer:'REAL_LISTENING_HTTP_SECURITY_H2_SYNTHETIC_ROWS_CLIENT_RESPONSE_TIMING_ONLY',apiPayloadMocked:false,externalMediaSubstituted:true,events,errors},null,2))
 }
})
