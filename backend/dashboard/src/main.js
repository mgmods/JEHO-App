import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'
import './assets/styles/main.css'
import './assets/styles/command-shell.css'
import { applyDashboardLocale, readDashboardLocale } from './utils/locale'

applyDashboardLocale(readDashboardLocale())

const app = createApp(App)
app.config.errorHandler = (err, _instance, info) => {
  // Log only — do not replace the whole admin shell on a single view crash
  // (Vue production code 1 = render function; see https://vuejs.org/error-reference/#runtime-1)
  console.error('Vue error:', err, info)
}
app.use(createPinia())
app.use(i18n)
app.use(router)
app.mount('#app')
const boot = document.getElementById('boot-fallback')
if (boot) boot.classList.remove('show')
