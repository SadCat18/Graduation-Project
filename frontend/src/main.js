import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles.css'

if (process.env.NODE_ENV === 'development') {
  window.addEventListener('error', (event) => {
    const message = String(event?.message || '').toLowerCase()
    const filename = String(event?.filename || '').toLowerCase()
    const isCrossScriptNoise =
      message === 'script error.' ||
      (filename.includes('webapi.amap.com') && message.includes('script error'))
    if (isCrossScriptNoise) {
      event.preventDefault()
      event.stopImmediatePropagation()
    }
  }, true)
}

createApp(App).use(router).mount('#app')
