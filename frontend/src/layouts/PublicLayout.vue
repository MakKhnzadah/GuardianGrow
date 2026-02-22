<template>
  <main class="layout layout--public">
    <RouterView />
  </main>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
</style>
