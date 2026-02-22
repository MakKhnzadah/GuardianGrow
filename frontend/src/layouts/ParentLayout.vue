<template>
  <div class="layout">
    <header class="header">
      <div class="title">GuardianGrow — Parent</div>
      <RouterLink to="/parent/dashboard">Dashboard</RouterLink>
      <RouterLink to="/parent/children">Children</RouterLink>
      <RouterLink to="/parent/library">Library</RouterLink>
      <div class="spacer" />
      <PvButton label="Logout" size="small" severity="secondary" @click="onLogout" />
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../features/auth/store'

const router = useRouter()
const auth = useAuthStore()

async function onLogout() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr;
}
.header {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
}
.title {
  font-weight: 600;
  margin-right: 12px;
}
.spacer {
  flex: 1;
}
.content {
  padding: 16px;
}
</style>
