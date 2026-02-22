<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

const planItemId = ref('')

async function go() {
  const childId = String(route.params.childId)
  if (!planItemId.value) return
  await router.push(`/child/${childId}/session/${planItemId.value}`)
}
</script>

<template>
  <section style="display: grid; gap: 12px; max-width: 520px; margin: 0 auto">
    <h1>Today’s Sessions</h1>
    <p>Only planned sessions—no infinite feed.</p>

    <PvCard>
      <template #title>Start a session</template>
      <template #content>
        <div style="display: grid; gap: 8px">
          <label>Plan item id</label>
          <PvInputText v-model="planItemId" placeholder="(from plan builder)" />
          <PvButton label="Open" :disabled="!planItemId" @click="go" />
        </div>
      </template>
    </PvCard>
  </section>
</template>
