<template>
  <div class="noise"></div>
  <div class="layout-shell">
    <aside class="side-rail">
      <div class="brand-block">
        <p class="eyebrow">gateway ops</p>
        <h1>CONTROL<br />DECK</h1>
        <p class="brand-copy">为 AI-Gateway 提供运行时配置、路由映射与热刷新控制。</p>
      </div>

      <nav class="nav-stack">
        <RouterLink class="nav-chip" active-class="active" to="/runtime">运行时状态</RouterLink>
        <RouterLink class="nav-chip" active-class="active" to="/provider">Provider 配置</RouterLink>
        <RouterLink class="nav-chip" active-class="active" to="/model-redirect">Redirect 配置</RouterLink>
      </nav>

      <div class="legend-card">
        <span class="legend-dot"></span>
        <div>
          <strong>工业霓虹主题</strong>
          <p>偏中控台的视觉风格，强调状态、层次与操作反馈。</p>
        </div>
      </div>
    </aside>

    <main class="main-stage">
      <section class="hero-banner">
        <div>
          <p class="eyebrow">runtime cockpit</p>
          <h2>配置即刻落盘，路由立即生效。</h2>
        </div>
        <div class="hero-actions">
          <slot name="hero-actions"></slot>
        </div>
      </section>

      <slot></slot>
    </main>
  </div>
</template>

<script setup lang="ts">
</script>
