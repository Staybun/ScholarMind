import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'highlight.js/styles/github-dark-dimmed.css'
import App from './App.vue'
import router from './router'
import './styles/global.scss'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
