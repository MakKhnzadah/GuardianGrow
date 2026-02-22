import { createApp } from 'vue'
import PrimeVue from 'primevue/config'
import Aura from '@primeuix/themes/aura'
import 'primeicons/primeicons.css'

import './style.css'
import App from './App.vue'
import { createAppRouter } from './app/router'
import { installPinia } from './app/pinia'
import { installVueQuery } from './app/query'
import { installPrimeVueComponents } from './app/primevue-components'

const app = createApp(App)

installPinia(app)
installVueQuery(app)

app.use(PrimeVue, {
	theme: {
		preset: Aura,
	},
})
installPrimeVueComponents(app)

app.use(createAppRouter())

app.mount('#app')
