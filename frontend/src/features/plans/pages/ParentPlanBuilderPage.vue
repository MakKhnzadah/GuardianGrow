<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import * as plansClient from '../../../api/clients/plans-client'

const route = useRoute()
const queryClient = useQueryClient()

const childId = computed(() => String(route.params.childId))

const contentId = ref('')
const dayOfWeek = ref<plansClient.DayOfWeek>('MON')
const targetMinutes = ref<number>(15)

const planQuery = useQuery({
  queryKey: ['plan', childId],
  queryFn: async () => await plansClient.getPlan(childId.value),
})

const addItemMutation = useMutation({
  mutationFn: async () => {
    if (!contentId.value) throw new Error('Missing contentId')
    return await plansClient.addPlanItem(childId.value, {
      contentId: contentId.value,
      dayOfWeek: dayOfWeek.value,
      targetMinutes: targetMinutes.value,
    })
  },
  onSuccess: async () => {
    contentId.value = ''
    await queryClient.invalidateQueries({ queryKey: ['plan'] })
  },
})

const dayOptions: Array<{ label: string; value: plansClient.DayOfWeek }> = [
  { label: 'Mon', value: 'MON' },
  { label: 'Tue', value: 'TUE' },
  { label: 'Wed', value: 'WED' },
  { label: 'Thu', value: 'THU' },
  { label: 'Fri', value: 'FRI' },
  { label: 'Sat', value: 'SAT' },
  { label: 'Sun', value: 'SUN' },
]
</script>

<template>
  <section style="display: grid; gap: 16px">
    <h1>Weekly Plan Builder</h1>
    <p>Build a weekly plan with ordering per day.</p>

    <PvCard>
      <template #title>Current plan</template>
      <template #content>
        <div v-if="planQuery.isLoading.value">Loading…</div>
        <div v-else-if="planQuery.isError.value">Failed to load plan.</div>
        <div v-else-if="planQuery.data.value">
          <div style="display: grid; gap: 6px">
            <div><strong>Week start:</strong> {{ planQuery.data.value.weekStart }}</div>
            <div>
              <strong>Limits:</strong> {{ planQuery.data.value.dailyTimeLimitMin }} min/day, break after
              {{ planQuery.data.value.breakAfterMin }} min for {{ planQuery.data.value.breakDurationMin }} min
            </div>
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Add plan item</template>
      <template #content>
        <div style="display: grid; gap: 12px; max-width: 720px">
          <div style="display: grid; gap: 6px">
            <label>Content id</label>
            <PvInputText v-model="contentId" placeholder="(copy from Content Library)" />
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px">
            <div style="display: grid; gap: 6px">
              <label>Day</label>
              <PvDropdown v-model="dayOfWeek" :options="dayOptions" option-label="label" option-value="value" />
            </div>
            <div style="display: grid; gap: 6px">
              <label>Target minutes</label>
              <PvInputNumber v-model="targetMinutes" :min="1" :max="180" />
            </div>
          </div>

          <div>
            <PvButton
              label="Add"
              :disabled="!contentId"
              :loading="addItemMutation.isPending.value"
              @click="addItemMutation.mutate()"
            />
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Plan items</template>
      <template #content>
        <div v-if="planQuery.data.value">
          <PvDataTable :value="planQuery.data.value.items" size="small">
            <PvColumn field="dayOfWeek" header="Day" />
            <PvColumn field="orderIndex" header="#" />
            <PvColumn field="contentId" header="Content" />
            <PvColumn field="targetMinutes" header="Minutes" />
            <PvColumn field="id" header="Plan item id" />
          </PvDataTable>
        </div>
      </template>
    </PvCard>
  </section>
</template>
