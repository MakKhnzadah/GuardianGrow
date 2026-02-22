<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import * as reportsClient from '../../../api/clients/reports-client'

const queryClient = useQueryClient()

const status = ref<reportsClient.ContentReportStatus | ''>('OPEN')
const statusOptions: Array<{ label: string; value: reportsClient.ContentReportStatus | '' }> = [
  { label: 'Open', value: 'OPEN' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Dismissed', value: 'DISMISSED' },
  { label: 'Any', value: '' },
]

const params = computed(() => ({ status: status.value || undefined }))
const reportsQuery = useQuery({
  queryKey: ['reports', 'content', params],
  queryFn: async () => await reportsClient.listContentReports(params.value),
})

const resolveMutation = useMutation({
  mutationFn: async (input: { reportId: string; resolution: 'RESOLVE' | 'DISMISS' }) =>
    await reportsClient.resolveContentReport(input.reportId, { resolution: input.resolution }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['reports'] })
  },
})
</script>

<template>
  <section style="display: grid; gap: 16px">
    <h1>Content Reports</h1>

    <PvCard>
      <template #title>Filters</template>
      <template #content>
        <div style="display: grid; grid-template-columns: 260px auto; gap: 12px; align-items: end">
          <div style="display: grid; gap: 6px">
            <label>Status</label>
            <PvDropdown v-model="status" :options="statusOptions" option-label="label" option-value="value" />
          </div>
          <div>
            <PvButton label="Refresh" severity="secondary" @click="reportsQuery.refetch()" />
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Reports</template>
      <template #content>
        <div v-if="reportsQuery.isLoading.value">Loading…</div>
        <div v-else-if="reportsQuery.isError.value">Failed to load reports.</div>

        <PvDataTable v-else :value="reportsQuery.data.value ?? []" size="small">
          <PvColumn field="status" header="Status" />
          <PvColumn field="contentId" header="Content" />
          <PvColumn field="reason" header="Reason" />
          <PvColumn field="createdAt" header="Created" />
          <PvColumn header="Actions">
            <template #body="slotProps">
              <div style="display: flex; gap: 8px">
                <PvButton
                  label="Resolve"
                  size="small"
                  :loading="resolveMutation.isPending.value"
                  @click="resolveMutation.mutate({ reportId: slotProps.data.id, resolution: 'RESOLVE' })"
                />
                <PvButton
                  label="Dismiss"
                  size="small"
                  severity="secondary"
                  :loading="resolveMutation.isPending.value"
                  @click="resolveMutation.mutate({ reportId: slotProps.data.id, resolution: 'DISMISS' })"
                />
              </div>
            </template>
          </PvColumn>
        </PvDataTable>
      </template>
    </PvCard>
  </section>
</template>
