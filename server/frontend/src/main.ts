import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// 样式顺序：Element 基础变量 → 设计令牌 → Element 主题覆盖
// (组件样式由 unplugin-vue-components 按需注入，这里只需引入全局变量层)
import 'element-plus/theme-chalk/base.css'
import './styles/tokens.css'
import './styles/element-overrides.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')