<template>
  <div class="container">
    <nav class="navbar">
      <h3 class="brand">Spark Performance Insight</h3>
      <div class="links">
        <router-link to="/">App List</router-link>
      </div>
    </nav>
    <router-view></router-view>
  </div>
</template>

<style scoped>
.container { min-height: 100vh; }
.navbar { background: #2c3e50; color: white; padding: 0.5rem 2rem; display: flex; justify-content: space-between; align-items: center; min-height: 40px; }
.brand { margin: 0; font-size: 1rem; font-weight: 500; opacity: 0.9; }
.links a { color: white; text-decoration: none; margin-left: 1rem; font-size: 0.85rem; opacity: 0.8; }
.links a:hover { opacity: 1; }
.links a.router-link-active { border-bottom: 2px solid #42b983; opacity: 1; }
</style>
