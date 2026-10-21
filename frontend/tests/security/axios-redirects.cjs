// Offline Node transport regressions. Servers bind only 127.0.0.1 and use dummy markers.
// No real accounts, credentials, proxy, external destination or production exploitation.
const assert = require('node:assert/strict')
const http = require('node:http')
const axios = require('axios')
const follow = require('follow-redirects')
const followVersion = require('follow-redirects/package.json').version
const axiosVersion = require('axios/package.json').version
const results = []
const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const close = server => new Promise(resolve => { server.close(resolve); server.closeAllConnections() })
const markers = { Authorization: 'SYNTHETIC_AUTH_ONLY', Cookie: 'dummy=LOCAL_ONLY', 'X-Regular': 'ordinary', 'X-Token^Secret': 'SYNTHETIC_CUSTOM_ONLY', 'X-Token|Alt': 'SYNTHETIC_CUSTOM_ONLY', 'X-Token': 'ordinary-sibling' }
const rawHeaders = Object.entries(markers).flat()
const sensitiveHeaders = ['X-Token^Secret', 'X-Token|Alt']
let destinationHits = 0, sourceUrl, destinationUrl
async function echo(req, res) {
  let body = ''
  for await (const chunk of req) body += chunk
  res.setHeader('Content-Type', 'application/json')
  res.end(JSON.stringify({ headers: req.headers, method: req.method, body }))
}
const destination = http.createServer((req,res)=>{destinationHits++;echo(req,res)})
const source = http.createServer((req,res)=>{
  const redirects = {
    '/same': [302, sourceUrl + '/echo'], '/cross': [302, destinationUrl + '/echo'],
    '/post307': [307, sourceUrl + '/echo'], '/post302': [302, sourceUrl + '/echo'],
    '/loop': [302, sourceUrl + '/loop']
  }
  if(redirects[req.url]) { const [status,location]=redirects[req.url];req.resume();res.writeHead(status,{Location:location});res.end();return }
  echo(req,res)
})
function direct(path, headers) {
  return new Promise((resolve,reject)=>{
    const req=follow.http.get(sourceUrl+path,{headers,sensitiveHeaders,maxRedirects:3,timeout:3000},res=>{
      let body='';res.setEncoding('utf8');res.on('data',chunk=>{body+=chunk});res.on('end',()=>{try{resolve(JSON.parse(body))}catch(e){reject(e)}})
    })
    req.on('error',reject);req.on('timeout',()=>req.destroy(new Error('local test timeout')))
  })
}
async function check(name, fn) { await fn();results.push({name,passed:true}) }
function stripped(headers) {
  for(const key of ['authorization','cookie','x-token^secret','x-token|alt']) assert.equal(headers[key],undefined,`${key} must be stripped`)
  assert.equal(headers['x-regular'],'ordinary')
  assert.equal(headers['x-token'],'ordinary-sibling','regex metacharacters must not strip sibling header')
}
;(async()=>{
  try {
    assert.equal(axiosVersion,'1.20.0');assert.equal(followVersion,'1.16.1')
    await listen(source);await listen(destination)
    sourceUrl=`http://127.0.0.1:${source.address().port}`;destinationUrl=`http://127.0.0.1:${destination.address().port}`
    const client=axios.create({adapter:'http',proxy:false,timeout:3000,httpAgent:new http.Agent({keepAlive:false})})
    await check('Axios direct GET/JSON transport',async()=>{const r=await client.get(sourceUrl+'/echo');assert.equal(r.status,200);assert.equal(r.data.method,'GET')})
    await check('Axios POST body survives normal transport',async()=>{const r=await client.post(sourceUrl+'/echo',{synthetic:true});assert.deepEqual(JSON.parse(r.data.body),{synthetic:true})})
    await check('Axios same-origin 302 retains explicit dummy auth/cookie',async()=>{const r=await client.get(sourceUrl+'/same',{headers:markers});assert.equal(r.data.headers.authorization,markers.Authorization);assert.equal(r.data.headers.cookie,markers.Cookie)})
    await check('Axios cross-origin 302 strips auth/cookie, keeps ordinary header',async()=>{const r=await client.get(sourceUrl+'/cross',{headers:markers});assert.equal(r.data.headers.authorization,undefined);assert.equal(r.data.headers.cookie,undefined);assert.equal(r.data.headers['x-regular'],'ordinary')})
    await check('Axios 307 preserves POST method/body',async()=>{const r=await client.post(sourceUrl+'/post307',{synthetic:true});assert.equal(r.data.method,'POST');assert.deepEqual(JSON.parse(r.data.body),{synthetic:true})})
    await check('Axios 302 POST changes to GET and clears entity',async()=>{const r=await client.post(sourceUrl+'/post302',{synthetic:true});assert.equal(r.data.method,'GET');assert.equal(r.data.body,'');assert.equal(r.data.headers['content-type'],undefined)})
    await check('Axios maxRedirects=0 never requests destination',async()=>{const before=destinationHits;await assert.rejects(client.get(sourceUrl+'/cross',{maxRedirects:0}),e=>e.response?.status===302);assert.equal(destinationHits,before)})
    await check('Axios redirect loop bound is enforced',async()=>{await assert.rejects(client.get(sourceUrl+'/loop',{maxRedirects:2}),e=>e.code==='ERR_FR_TOO_MANY_REDIRECTS')})
    await check('GHSA-q3jh array headers: same-origin positive retention',async()=>{const r=await direct('/same',rawHeaders);assert.equal(r.headers.authorization,markers.Authorization);assert.equal(r.headers.cookie,markers.Cookie)})
    await check('GHSA-q3jh array headers: cross-origin negative stripping',async()=>{stripped((await direct('/cross',rawHeaders)).headers)})
    await check('GHSA-8r9p literal regex headers: same-origin positive retention',async()=>{const r=await direct('/same',markers);assert.equal(r.headers['x-token^secret'],markers['X-Token^Secret']);assert.equal(r.headers['x-token|alt'],markers['X-Token|Alt'])})
    await check('GHSA-8r9p literal regex headers: cross-origin negative stripping without sibling deletion',async()=>{stripped((await direct('/cross',markers)).headers)})
    console.log(JSON.stringify({layer:'LOCAL_LOOPBACK_SYNTHETIC_NO_EXTERNAL_REQUESTS',axiosVersion,followVersion,passed:results.length,failed:0,tests:results},null,2))
  } finally { await Promise.all([close(source),close(destination)]) }
})().catch(error=>{console.error(JSON.stringify({passed:results.length,failed:1,error:error.message,tests:results},null,2));process.exitCode=1})
