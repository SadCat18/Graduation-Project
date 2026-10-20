// Synthetic props, actual Vue components/ECharts/ResizeObserver; no business API acceptance.
const fs = require('node:fs')
const path = require('node:path')
const { test, expect } = require('@playwright/test')

test('actual charts recover from loading/empty/error and resize with their container', async ({page}, info) => {
  test.setTimeout(60000)
  const root=path.resolve(__dirname,'..'), fixture=path.join(root,'test-results/chart-lifecycle-harness')
  fs.mkdirSync(fixture,{recursive:true})
  fs.writeFileSync(path.join(fixture,'index.html'),'<div id="app"></div><script type="module" src="./entry.js"></script>')
  fs.writeFileSync(path.join(fixture,'entry.js'),`
    import { createApp, h, reactive } from 'vue'
    import Trend from '/src/views/admin/dashboard/AdminTrendChart.vue'
    import Donut from '/src/views/admin/dashboard/AdminDonutChart.vue'
    import Funnel from '/src/views/admin/dashboard/AdminFunnelChart.vue'
    import echarts from '/src/views/admin/dashboard/echarts.js'
    const state=reactive({loading:true,empty:false,error:'',width:840})
    const rows=[{date:'2026-10-19',newUsers:4,newPosts:2,newActivities:1,newSigns:5},{date:'2026-10-20',newUsers:8,newPosts:5,newActivities:3,newSigns:9}]
    const actions={data(){Object.assign(state,{loading:false,empty:false,error:''})},empty(){Object.assign(state,{loading:false,empty:true,error:''})},error(){Object.assign(state,{loading:false,error:'合成故障'})},loading(){state.loading=true},narrow(){state.width=480}}
    window.chartInstances=()=>Array.from(document.querySelectorAll('.trend-chart,.donut-chart,.funnel-chart')).map(el=>echarts.getInstanceByDom(el)).filter(Boolean)
    window.chartHarness=createApp({setup(){return()=>h('main',[
      h('nav',Object.keys(actions).map(name=>h('button',{onClick:actions[name]},name))),
      h('div',{style:{width:state.width+'px'}},[
        h(Trend,{rows:state.empty?[]:rows,loading:state.loading,error:state.error}),
        h(Donut,{items:state.empty?[]:[{type:'合成分类',count:3},{type:'另一分类',count:2}],loading:state.loading,error:state.error}),
        h(Funnel,{conversion:state.empty?{}:{views:10,signs:3,rate:30},loading:state.loading,error:state.error})
      ])
    ])}}); window.chartHarness.mount('#app')
  `)
  const {createServer}=await import('vite'), {default:vue}=await import('@vitejs/plugin-vue')
  const server=await createServer({root,configFile:false,plugins:[vue()],logLevel:'error',cacheDir:path.join(fixture,'cache'),server:{host:'127.0.0.1',port:0,fs:{allow:[root,fs.realpathSync(path.join(root,'node_modules'))]}}})
  const errors=[]
  page.on('pageerror',error=>errors.push(error.message))
  page.on('console',message=>{if(message.type()==='error')errors.push(message.text())})
  try {
    await server.listen()
    await page.setViewportSize({width:1000,height:900})
    await page.goto(server.resolvedUrls.local[0]+'test-results/chart-lifecycle-harness/index.html')
    await expect(page.getByRole('status')).toHaveCount(3)
    await page.getByRole('button',{name:'data',exact:true}).click()
    const canvases=page.locator('.trend-chart canvas,.donut-chart canvas,.funnel-chart canvas')
    await expect(canvases).toHaveCount(3)
    for(const state of ['empty','error','loading']) {
      await page.evaluate(()=>{window.previousCharts=window.chartInstances()})
      await page.getByRole('button',{name:state,exact:true}).click()
      await expect(canvases).toHaveCount(0)
      await expect.poll(()=>page.evaluate(()=>window.previousCharts.every(chart=>chart.isDisposed()))).toBe(true)
      await page.getByRole('button',{name:'data',exact:true}).click()
      await expect(canvases).toHaveCount(3)
      expect(await page.evaluate(()=>window.chartInstances().every(chart=>!window.previousCharts.includes(chart)))).toBe(true)
    }
    const viewport=await page.evaluate(()=>window.innerWidth)
    const widths=await page.evaluate(()=>window.chartInstances().map(chart=>chart.getWidth()))
    await page.getByRole('button',{name:'narrow',exact:true}).click()
    await expect.poll(()=>page.evaluate(()=>window.chartInstances().map(chart=>chart.getWidth()))).not.toEqual(widths)
    expect(await page.evaluate(()=>window.innerWidth)).toBe(viewport)
    expect(await page.evaluate(()=>window.chartInstances().every(chart=>chart.getWidth()===chart.getDom().clientWidth))).toBe(true)
    for(const selector of ['.trend-chart','.donut-chart','.funnel-chart']) {
      const chart=page.locator(selector), canvas=chart.locator('canvas').first()
      await chart.scrollIntoViewIfNeeded(); await page.mouse.move(0,0); await page.waitForTimeout(650)
      const before=await canvas.screenshot(), box=await chart.boundingBox()
      const point=selector==='.donut-chart'?{x:box.width*.34+box.height*.3,y:box.height*.5}:{x:box.width*.5,y:box.height*.3}
      await chart.hover({position:point}); await page.waitForTimeout(350)
      expect((await canvas.screenshot()).equals(before),selector+' should render hover').toBe(false)
      await chart.screenshot({path:info.outputPath(selector.slice(1)+'.png')})
    }
    await page.evaluate(()=>{window.previousCharts=window.chartInstances();window.chartHarness.unmount()})
    await expect(canvases).toHaveCount(0)
    expect(await page.evaluate(()=>window.previousCharts.every(chart=>chart.isDisposed()))).toBe(true)
    expect(errors).toEqual([])
  } finally { await server.close() }
})
