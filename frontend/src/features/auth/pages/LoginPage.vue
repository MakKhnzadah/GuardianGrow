<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store'

const email = ref('')
const password = ref('')
const busy = ref(false)
const error = ref<string | null>(null)

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

async function onSubmit() {
  busy.value = true
  error.value = null
  try {
    await auth.login(email.value, password.value)
    const next = typeof route.query.next === 'string' ? route.query.next : '/parent/dashboard'
    await router.replace(next)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Login failed'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="card">
    <h1>Login</h1>
    <div class="row">
      <label>Email</label>
      <PvInputText v-model="email" autocomplete="email" />
    </div>
    <div class="row">
      <label>Password</label>
      <PvPassword v-model="password" :feedback="false" toggle-mask autocomplete="current-password" />
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="actions">
      <PvButton label="Sign in" :loading="busy" @click="onSubmit" />
      <RouterLink to="/register">Create account</RouterLink>
    </div>
  </section>
</template>

<style scoped>
.card {
  width: min(440px, 100%);
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 16px;
}
.row {
  display: grid;
  gap: 6px;
  margin: 12px 0;
}
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
</style>
