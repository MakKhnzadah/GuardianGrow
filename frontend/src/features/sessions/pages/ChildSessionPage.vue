<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSessionMachine } from '../useSessionMachine'

const route = useRoute()
const router = useRouter()

const childId = computed(() => String(route.params.childId))
const planItemId = computed(() => String(route.params.planItemId))

const machine = useSessionMachine({ childId: childId.value, planItemId: planItemId.value })

function formatMmSs(totalSeconds: number) {
  const mm = Math.floor(totalSeconds / 60)
  const ss = totalSeconds % 60
  return `${mm}:${String(ss).padStart(2, '0')}`
}

async function finish() {
  await machine.end('EXIT')
  await router.replace(`/child/${childId.value}/done`)
}
</script>

<template>
  <section style="display: grid; gap: 16px; max-width: 720px; margin: 0 auto">
    <h1>Session</h1>

    <div v-if="machine.error.value" style="opacity: 0.9">
      {{ machine.error.value }}
    </div>

    <PvCard>
      <template #title>
        <span v-if="machine.view.value?.contentTitle">{{ machine.view.value.contentTitle }}</span>
        <span v-else>Planned session</span>
      </template>
      <template #content>
        <div v-if="!machine.sessionId.value" style="display: grid; gap: 12px">
          <p>Start when you are ready. The session will stop at a clear endpoint.</p>
          <PvButton label="Start" :loading="machine.busy.value" @click="machine.start()" />
        </div>

        <div v-else style="display: grid; gap: 12px">
          <div>State: {{ machine.state.value }}</div>
          <div v-if="machine.view.value">
            <div>Time remaining: {{ formatMmSs(machine.view.value.secondsToHardStop) }}</div>
          </div>

          <div v-if="machine.state.value === 'FORCED_BREAK'">
            <h2 style="margin: 0">Break time</h2>
            <div v-if="machine.view.value && machine.view.value.secondsToResume !== null">
              Resume in: {{ formatMmSs(machine.view.value.secondsToResume) }}
            </div>
          </div>

          <div style="display: flex; gap: 12px">
            <PvButton label="End" severity="secondary" :loading="machine.busy.value" @click="finish" />
          </div>
        </div>
      </template>
    </PvCard>
  </section>
</template>
