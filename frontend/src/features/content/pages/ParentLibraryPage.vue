<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import * as contentClient from '../../../api/clients/content-client'

const router = useRouter()

const q = ref('')
const topic = ref('')
const type = ref<contentClient.ContentType | ''>('')
const age = ref<number | null>(null)
const difficulty = ref<number | null>(null)
const minDuration = ref<number | null>(null)
const maxDuration = ref<number | null>(null)

const params = computed<contentClient.ContentSearchParams>(() => ({
  q: q.value || undefined,
  topic: topic.value || undefined,
  type: type.value || undefined,
  age: age.value ?? undefined,
  difficulty: difficulty.value ?? undefined,
  minDuration: minDuration.value ?? undefined,
  maxDuration: maxDuration.value ?? undefined,
  page: 1,
  pageSize: 50,
  sort: 'createdAt,desc',
}))

const contentQuery = useQuery({
  queryKey: ['content', params],
  queryFn: async () => await contentClient.searchContent(params.value),
})

const typeOptions: Array<{ label: string; value: contentClient.ContentType | '' }> = [
  { label: 'Any', value: '' },
  { label: 'Lesson', value: 'LESSON' },
  { label: 'Story', value: 'STORY' },
  { label: 'Puzzle', value: 'PUZZLE' },
]

function openItem(row: contentClient.ContentListItem) {
  router.push(`/parent/content/${row.id}`)
}
</script>

<template>
  <section style="display: grid; gap: 16px">
    <h1>Content Library</h1>

    <PvCard>
      <template #title>Filters</template>
      <template #content>
        <div
          style="
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 12px;
            align-items: end;
          "
        >
          <div style="display: grid; gap: 6px">
            <label>Search</label>
            <PvInputText v-model="q" placeholder="fractions" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Topic</label>
            <PvInputText v-model="topic" placeholder="math" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Type</label>
            <PvDropdown v-model="type" :options="typeOptions" option-label="label" option-value="value" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Age</label>
            <PvInputNumber v-model="age" :min="0" :max="18" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Difficulty (1–5)</label>
            <PvInputNumber v-model="difficulty" :min="1" :max="5" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Min duration (min)</label>
            <PvInputNumber v-model="minDuration" :min="0" :max="300" />
          </div>

          <div style="display: grid; gap: 6px">
            <label>Max duration (min)</label>
            <PvInputNumber v-model="maxDuration" :min="0" :max="300" />
          </div>

          <div>
            <PvButton label="Refresh" severity="secondary" @click="contentQuery.refetch()" />
          </div>
        </div>
      </template>
    </PvCard>

    <PvCard>
      <template #title>Results</template>
      <template #content>
        <div v-if="contentQuery.isLoading.value">Loading…</div>
        <div v-else-if="contentQuery.isError.value">Failed to load content.</div>
        <PvDataTable
          v-else
          :value="contentQuery.data.value ?? []"
          size="small"
          selection-mode="single"
          @row-select="(e: any) => openItem(e.data)"
        >
          <PvColumn field="title" header="Title" />
          <PvColumn field="type" header="Type" />
          <PvColumn field="topic" header="Topic" />
          <PvColumn header="Age">
            <template #body="slotProps">
              {{ slotProps.data.minAge }}–{{ slotProps.data.maxAge }}
            </template>
          </PvColumn>
          <PvColumn field="difficulty" header="Difficulty" />
          <PvColumn field="estMinutes" header="Est. min" />
          <PvColumn field="status" header="Status" />
        </PvDataTable>
      </template>
    </PvCard>
  </section>
</template>
