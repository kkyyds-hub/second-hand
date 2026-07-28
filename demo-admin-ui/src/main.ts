import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { buildAdminDocumentTitle } from './utils/adminBrand'

/**
 * 应用入口尽量保持很薄，只负责把全局能力按固定顺序挂到根实例上。
 * 后续 review 如果遇到“插件没生效 / 路由守卫异常”，先从这里检查注册顺序。
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)

void router.isReady().then(() => {
  const title = router.currentRoute.value.meta.title
  document.title = buildAdminDocumentTitle(typeof title === 'string' ? title : '运营工作台')
})

app.mount('#app')
