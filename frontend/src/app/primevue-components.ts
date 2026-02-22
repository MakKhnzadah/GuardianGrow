import type { App } from 'vue'

import Button from 'primevue/button'
import Card from 'primevue/card'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Dropdown from 'primevue/dropdown'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Password from 'primevue/password'

export function installPrimeVueComponents(app: App) {
  app.component('PvButton', Button)
  app.component('PvCard', Card)
  app.component('PvDataTable', DataTable)
  app.component('PvColumn', Column)
  app.component('PvDropdown', Dropdown)
  app.component('PvInputNumber', InputNumber)
  app.component('PvInputText', InputText)
  app.component('PvTextarea', Textarea)
  app.component('PvPassword', Password)
}
