<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useAuthStore } from '../../auth/store'
import * as childrenClient from '../../../api/clients/children-client'

const auth = useAuthStore()
const householdId = computed(() => auth.householdId)

const displayName = ref('')
const birthDate = ref('2018-01-01')
const avatarKey = ref('')

const queryClient = useQueryClient()

const childrenQuery = useQuery({
  queryKey: ['children', householdId],
  enabled: computed(() => Boolean(householdId.value)),
  queryFn: async () => {
    if (!householdId.value) return []
    return await childrenClient.listChildren(householdId.value)
  },
})

const createMutation = useMutation({
  mutationFn: async () => {
    if (!householdId.value) throw new Error('Missing householdId')
    return await childrenClient.createChild(householdId.value, {
      displayName: displayName.value,
      birthDate: birthDate.value,
      avatarKey: avatarKey.value || undefined,
    })
  },
  onSuccess: async () => {
    displayName.value = ''
    avatarKey.value = ''
    await queryClient.invalidateQueries({ queryKey: ['children'] })
  },
})
</script>

<template>
  <section style="display: grid; gap: 16px">
    <h1>Children</h1>

    <PvCard>
      <template #title>Create child</template>
      <template #content>
        <div style="display: grid; gap: 12px; max-width: 520px">
          <div style="display: grid; gap: 6px">
            <label>Display name</label>
            <PvInputText v-model="displayName" />
          </div>
          <div style="display: grid; gap: 6px">
            <label>Birth date (YYYY-MM-DD)</label>
            <PvInputText v-model="birthDate" />
          </div>
          <div style="display: grid; gap: 6px">
            <label>Avatar key (optional)</label>
            <PvInputText v-model="avatarKey" placeholder="fox_1" />
          </div>

          <div>
            <PvButton
              label="Create"
              :disabled="!householdId || !displayName"
              :loading="createMutation.isPending.value"
              @click="createMutation.mutate()"
            />
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Child profiles</template>
      <template #content>
        <div v-if="childrenQuery.isLoading.value">Loading…</div>
        <div v-else-if="childrenQuery.isError.value">Failed to load children.</div>
        <PvDataTable v-else :value="childrenQuery.data.value ?? []" size="small">
          <PvColumn field="displayName" header="Name" />
          <PvColumn field="birthDate" header="Birth date" />
          <PvColumn field="status" header="Status" />
        </PvDataTable>
      </template>
    </PvCard>
  </section>
</template>
