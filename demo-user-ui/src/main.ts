import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'

/**
 * Pinia 先作为主工程标准能力保留。
 * Day01 仍以页面内状态和 request/session 工具为主，不额外引入全局 store 复杂度。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
