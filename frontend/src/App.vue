<template>
  <div class="container">
    <nav class="navbar">
      <h1>Spark Performance Insight</h1>
      <div class="links">
        <router-link to="/">App List</router-link>
      </div>
    </nav>
    <router-view></router-view>
  </div>
</template>

<style scoped>
.container { min-height: 100vh; }
.navbar { background: #2c3e50; color: white; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; }
.links a { color: white; text-decoration: none; margin-left: 1rem; font-weight: bold; }
.links a.router-link-active { border-bottom: 2px solid #42b983; }
</style>
