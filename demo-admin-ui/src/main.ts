import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'

/**
 * 应用入口尽量保持很薄，只负责把全局能力按固定顺序挂到根实例上。
 * 后续 review 如果遇到“插件没生效 / 路由守卫异常”，先从这里检查注册顺序。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
