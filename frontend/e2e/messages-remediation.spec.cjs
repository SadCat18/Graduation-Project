// Synthetic delayed/business-error responses; no real backend or production acceptance.
const { test, expect } = require('@playwright/test')
const ok = data => ({code:200,message:'synthetic',data})
const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><circle cx="16" cy="16" r="15" fill="#087a6d"/></svg>'
const avatar = 'data:image/svg+xml,'+encodeURIComponent(svg)
const conversation = id => ({conversationId:id,otherUserId:id+8,otherUsername:`合成会话${id}`,otherAvatar:avatar,unreadCount:0})
const message = (id,number) => ({messageId:number,conversationId:id,senderId:id+8,receiverId:7,content:`合成线程${id}消息${number}`})

async function mock(page, handler) {
  await page.addInitScript(()=>{localStorage.setItem('skate_token','SYNTHETIC');localStorage.setItem('skate_role','USER')})
  await page.route('https://www.beian.gov.cn/img/ghs.png',route=>route.fulfill({contentType:'image/svg+xml',body:svg}))
  await page.route('**/api/**',async route=>{
    const request=route.request(), url=new URL(request.url()), path=url.pathname
    const custom=await handler({route,request,url,path})
    if(custom===true)return
    let data=custom
    if(data===undefined) {
      if(path==='/api/user/profile')data={userId:7,username:'合成用户'}
      else if(path.endsWith('/unread-count'))data={unreadCount:0}
      else data={}
    }
    await route.fulfill({json:ok(data)})
  })
}

test('conversation page 2 failure retries page 2 before page 3 without duplicate rows',async({page})=>{
  const pages=[];let attempts=0
  await mock(page,async({route,url,path})=>{
    if(path!=='/api/messages/conversations')return
    const number=Number(url.searchParams.get('page'));pages.push(number)
    if(number===2&&++attempts===1) {await route.fulfill({json:{code:500,message:'合成page2失败',data:null}});return true}
    return {list:number===1?[conversation(1)]:number===2?[conversation(1),conversation(2)]:[conversation(3)],total:3,totalPages:3}
  })
  await page.goto('/messages')
  const more=page.getByRole('button',{name:'加载更多会话',exact:true})
  await more.click();await expect(page.getByText('合成page2失败')).toBeVisible();await expect(more).toBeEnabled()
  await more.click();await expect(page.locator('.conversation-item')).toHaveCount(2)
  await more.click();await expect(page.locator('.conversation-item')).toHaveCount(3)
  expect(pages).toEqual([1,2,2,3])
})

test('slow A response cannot replace fast B, mark A read, or replace B older cursor',async({page})=>{
  let releaseA;const waiting=new Promise(resolve=>{releaseA=resolve}),reads=[],older=[],errors=[]
  page.on('pageerror',error=>errors.push(error.message))
  await mock(page,async({route,request,url,path})=>{
    if(path==='/api/messages/conversations')return {list:[conversation(1),conversation(2)],total:2,totalPages:1}
    const match=path.match(/\/conversations\/(\d+)\/(messages|read)$/)
    if(!match)return
    const id=Number(match[1])
    if(match[2]==='read'){reads.push({id,...request.postDataJSON()});return {}}
    if(id===1){await waiting;await route.fulfill({json:ok({list:[message(1,10)],nextBeforeMessageId:10,hasMore:true})});return true}
    if(url.searchParams.has('beforeMessageId')){older.push(url.searchParams.get('beforeMessageId'));return {list:[message(2,15)],hasMore:false,nextBeforeMessageId:null}}
    return {list:[message(2,20)],hasMore:true,nextBeforeMessageId:20}
  })
  await page.goto('/messages/1')
  await page.locator('.conversation-item').filter({hasText:'合成会话2'}).click()
  await expect(page.locator('.thread-identity h2')).toHaveText('合成会话2')
  await expect(page.getByText('合成线程2消息20')).toBeVisible()
  const response=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/conversations/1/messages'))
  releaseA();await response;await page.evaluate(()=>new Promise(requestAnimationFrame))
  await expect(page.getByText('合成线程1消息10')).toHaveCount(0)
  await page.getByRole('button',{name:'加载更早消息',exact:true}).click()
  await expect(page.getByText('合成线程2消息15')).toBeVisible()
  expect(older).toEqual(['20']);expect(reads).toEqual([{id:2,lastReadMessageId:20}]);expect(errors).toEqual([])
})
