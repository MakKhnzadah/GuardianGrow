<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useAuthStore } from '../../auth/store'
import * as authClient from '../../../api/clients/auth-client'

const auth = useAuthStore()

const meQuery = useQuery({
  queryKey: ['me'],
  enabled: computed(() => Boolean(auth.accessToken)),
  queryFn: async () => {
    if (!auth.accessToken) throw new Error('Missing access token')
    return await authClient.me(auth.accessToken)
  },
})
</script>

<template>
  <section>
    <h1>Parent Dashboard</h1>
    <p>Household overview, today’s sessions, and alerts.</p>

    <div v-if="meQuery.isLoading.value">Loading profile…</div>
    <div v-else-if="meQuery.isError.value" class="error">
      Failed to load profile: {{ meQuery.error.value instanceof Error ? meQuery.error.value.message : 'Unknown error' }}
    </div>
    <div v-else-if="meQuery.data.value">
      <p><strong>User:</strong> {{ meQuery.data.value.userId }}</p>
      <p><strong>Role:</strong> {{ meQuery.data.value.role }}</p>
    </div>
  </section>
</template>
