<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import * as moderationClient from '../../../api/clients/moderation-client'

const router = useRouter()

const status = ref<moderationClient.ModerationStatus | ''>('PENDING')

const statusOptions: Array<{ label: string; value: moderationClient.ModerationStatus | '' }> = [
  { label: 'Any', value: '' },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Changes requested', value: 'CHANGES_REQUESTED' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
]

const params = computed(() => ({ status: status.value || undefined }))

const queueQuery = useQuery({
  queryKey: ['moderation', 'queue', params],
  queryFn: async () => await moderationClient.listQueue(params.value),
})

function openItem(row: moderationClient.ModerationQueueItem) {
  router.push(`/admin/moderation/${row.id}`)
}
</script>

<template>
  <section style="display: grid; gap: 16px">
    <h1>Moderation Queue</h1>

    <PvCard>
      <template #title>Filters</template>
      <template #content>
        <div style="display: grid; grid-template-columns: 260px auto; gap: 12px; align-items: end">
          <div style="display: grid; gap: 6px">
            <label>Status</label>
            <PvDropdown v-model="status" :options="statusOptions" option-label="label" option-value="value" />
          </div>
          <div>
            <PvButton label="Refresh" severity="secondary" @click="queueQuery.refetch()" />
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Queue</template>
      <template #content>
        <div v-if="queueQuery.isLoading.value">Loading…</div>
        <div v-else-if="queueQuery.isError.value">Failed to load moderation queue.</div>
        <PvDataTable
          v-else
          :value="queueQuery.data.value ?? []"
          size="small"
          selection-mode="single"
          @row-select="(e: any) => openItem(e.data)"
        >
          <PvColumn field="status" header="Status" />
          <PvColumn field="contentId" header="Content" />
          <PvColumn field="versionId" header="Version" />
          <PvColumn field="submittedAt" header="Submitted" />
        </PvDataTable>
      </template>
    </PvCard>
  </section>
</template>
