<template>
  <ConsoleLayout>
    <div class="page-card">
      <div class="card-header">
        <span class="card-header__title">模型配置</span>
      </div>

      <el-tabs v-model="activeTab" class="model-config-tabs">
        <el-tab-pane label="智能路由" name="auto-route">
          <AutoRouteTab @error="loadError = true" />
        </el-tab-pane>
        <el-tab-pane label="模型支持" name="supported-model">
          <SupportedModelView />
        </el-tab-pane>
      </el-tabs>
    </div>
  </ConsoleLayout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ConsoleLayout from '../../layout/ConsoleLayout.vue'
import AutoRouteTab from './AutoRouteTab.vue'
import SupportedModelView from './SupportedModelView.vue'

const activeTab = ref('auto-route')
const loadError = ref(false)
</script>

<style scoped>
.model-config-tabs {
  margin-top: 4px;
}
</style>
