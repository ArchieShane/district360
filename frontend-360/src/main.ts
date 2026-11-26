import { createApp } from 'vue'
import 'maplibre-gl/dist/maplibre-gl.css'
import './style.css'
import App from './App.vue'
import { initLang } from './i18n'

createApp(App).mount('#app')
// 中 / 英文：按上次的选择生效（英文时把页面文字就地换成英文）
initLang()
