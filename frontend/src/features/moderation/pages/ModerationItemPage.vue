<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import * as moderationClient from '../../../api/clients/moderation-client'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const queueItemId = computed(() => String(route.params.queueItemId))

const itemQuery = useQuery({
  queryKey: ['moderation', 'item', queueItemId],
  queryFn: async () => await moderationClient.getQueueItem(queueItemId.value),
})

const decision = ref<'APPROVE' | 'REJECT' | 'REQUEST_CHANGES'>('APPROVE')
const notes = ref('')

const decisionOptions = [
  { label: 'Approve', value: 'APPROVE' },
  { label: 'Reject', value: 'REJECT' },
  { label: 'Request changes', value: 'REQUEST_CHANGES' },
]

const reviewMutation = useMutation({
  mutationFn: async () => await moderationClient.reviewQueueItem(queueItemId.value, { decision: decision.value, notes: notes.value }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['moderation'] })
    await router.replace('/admin/moderation')
  },
})

const publishMutation = useMutation({
  mutationFn: async () => {
    const item = itemQuery.data.value
    if (!item) throw new Error('Missing item')
    await moderationClient.publishContent(item.contentId, { versionId: item.versionId })
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['moderation'] })
  },
})
</script>

<template>
  <section style="display: grid; gap: 16px; max-width: 960px">
    <h1>Moderation Item</h1>

    <div v-if="itemQuery.isLoading.value">Loading…</div>
    <div v-else-if="itemQuery.isError.value">Failed to load moderation item.</div>

    <template v-else>
      <PvCard>
        <template #title>Details</template>
        <template #content>
          <div style="display: grid; gap: 6px">
            <div><strong>Status:</strong> {{ itemQuery.data.value?.status }}</div>
            <div><strong>Content:</strong> {{ itemQuery.data.value?.contentId }}</div>
            <div><strong>Version:</strong> {{ itemQuery.data.value?.versionId }}</div>
          </div>
        </template>
      </PvCard>

      <PvCard>
        <template #title>Diff Viewer</template>
        <template #content>
          <p>TODO: show JSON diff between versions.</p>
        </template>
      </PvCard>

      <PvCard>
        <template #title>Review</template>
        <template #content>
          <div style="display: grid; gap: 12px; max-width: 520px">
            <div style="display: grid; gap: 6px">
              <label>Decision</label>
              <PvDropdown v-model="decision" :options="decisionOptions" option-label="label" option-value="value" />
            </div>
            <div style="display: grid; gap: 6px">
              <label>Notes</label>
              <PvTextarea v-model="notes" rows="5" auto-resize />
            </div>
            <div style="display: flex; gap: 12px">
              <PvButton label="Submit review" :loading="reviewMutation.isPending.value" @click="reviewMutation.mutate()" />
              <PvButton
                label="Publish version"
                severity="secondary"
                :loading="publishMutation.isPending.value"
                @click="publishMutation.mutate()"
              />
            </div>
          </div>
        </template>
      </PvCard>
    </template>
  </section>
</template>
