<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import * as contentClient from '../../../api/clients/content-client'

const route = useRoute()
const contentId = computed(() => String(route.params.contentId))

const contentQuery = useQuery({
  queryKey: ['content', 'detail', contentId],
  queryFn: async () => await contentClient.getContent(contentId.value),
})
</script>

<template>
  <section style="display: grid; gap: 12px">
    <h1>Content Preview</h1>
    <div v-if="contentQuery.isLoading.value">Loading…</div>
    <div v-else-if="contentQuery.isError.value">Failed to load content.</div>
    <div v-else>
      <h2 style="margin: 0">{{ contentQuery.data.value?.title }}</h2>
      <div>Type: {{ contentQuery.data.value?.type }}</div>
      <div>Topic: {{ contentQuery.data.value?.topic ?? '-' }}</div>
      <div>
        Age: {{ contentQuery.data.value?.minAge }}–{{ contentQuery.data.value?.maxAge }}
      </div>
      <div>Difficulty: {{ contentQuery.data.value?.difficulty ?? '-' }}</div>
      <div>Estimated minutes: {{ contentQuery.data.value?.estMinutes ?? '-' }}</div>

      <PvButton label="Assign to plan" severity="secondary" disabled />
    </div>
  </section>
</template>
