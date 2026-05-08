<template>
  <el-drawer v-model="visible" title="请求链路追踪" size="680px" destroy-on-close>
    <template v-if="loading">
      <div class="detail-loading">
        <el-skeleton :rows="12" animated />
      </div>
    </template>
    <template v-else-if="data">
      <!-- 请求概况头部 -->
      <div class="trace-header">
        <div class="trace-header__id">
          <span class="trace-header__label">请求 ID</span>
          <span class="trace-header__value">{{ data.requestId }}</span>
          <button v-if="data.requestId" class="trace-header__copy" type="button" @click="copyText(data.requestId)">
            复制
          </button>
        </div>
        <div class="trace-header__meta">
          <el-tag :type="overallStatusType" size="small" effect="dark">
            {{ overallStatusLabel }}
          </el-tag>
          <span v-if="data.durationMs != null" class="trace-header__duration">
            总耗时 {{ formatDuration(data.durationMs) }}
          </span>
          <span class="trace-header__time">{{ formatTime(data.createTime) }}</span>
        </div>
      </div>

      <!-- 时间线 -->
      <div class="trace-timeline">
        <!-- 阶段1：请求概况 -->
        <div class="timeline-node" :class="['timeline-node--' + getStageStatus('overview')]">
          <div class="timeline-dot">
            <el-icon><Document /></el-icon>
          </div>
          <div class="timeline-content">
            <div class="timeline-title">请求概况</div>
            <div class="timeline-body">
              <div class="info-grid">
                <InfoItem label="请求路径" :value="data.requestPath" />
                <InfoItem label="HTTP 方法" :value="data.httpMethod" />
                <InfoItem label="响应协议" :value="data.responseProtocol" />
                <InfoItem label="模型别名" :value="data.aliasModel" />
                <InfoItem label="目标模型" :value="data.targetModel" />
                <InfoItem label="通道" :value="data.providerCode" />
                <InfoItem label="来源 IP" :value="maskIp(data.sourceIp)" />
                <InfoItem label="流式请求" :value="booleanLabel(data.isStream)" />
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段2：鉴权 -->
        <div class="timeline-node" :class="['timeline-node--' + getStageStatus('auth')]">
          <div class="timeline-dot">
            <el-icon><Key /></el-icon>
          </div>
          <div class="timeline-content">
            <div class="timeline-title">
              鉴权阶段
              <el-tag
                v-if="data.rateLimitTriggered"
                type="danger"
                size="small"
                effect="plain"
                class="stage-tag"
              >
                限流触发
              </el-tag>
            </div>
            <div class="timeline-body">
              <div class="info-grid">
                <InfoItem label="API Key 前缀" :value="data.apiKeyPrefix" />
                <InfoItem label="鉴权状态" :value="authStatusLabel" />
                <InfoItem label="命中限流" :value="booleanLabel(data.rateLimitTriggered)" :highlight="Boolean(data.rateLimitTriggered)" />
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段3：路由 -->
        <div class="timeline-node" :class="['timeline-node--' + getStageStatus('routing')]">
          <div class="timeline-dot">
            <el-icon><Share /></el-icon>
          </div>
          <div class="timeline-content">
            <div class="timeline-title">路由阶段</div>
            <div class="timeline-body">
              <div class="info-grid">
                <InfoItem label="候选路由数" :value="formatNullableNumber(data.candidateCount)" />
                <InfoItem label="请求别名" :value="data.aliasModel" />
                <InfoItem label="最终路由" :value="data.targetModel" />
                <InfoItem label="提供商类型" :value="providerLabel(data.providerType)" />
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段4：调用 -->
        <div class="timeline-node" :class="['timeline-node--' + getStageStatus('upstream')]">
          <div class="timeline-dot">
            <el-icon><Promotion /></el-icon>
          </div>
          <div class="timeline-content">
            <div class="timeline-title">
              调用阶段
              <template v-if="hasGovernanceSignals(data)">
                <el-tag v-if="(data.retryCount ?? 0) > 0" type="warning" size="small" effect="plain" class="stage-tag">
                  重试 {{ data.retryCount }}
                </el-tag>
                <el-tag v-if="(data.failoverCount ?? 0) > 0" type="danger" size="small" effect="plain" class="stage-tag">
                  Failover {{ data.failoverCount }}
                </el-tag>
                <el-tag v-if="(data.circuitOpenSkippedCount ?? 0) > 0" type="info" size="small" effect="plain" class="stage-tag">
                  熔断跳过 {{ data.circuitOpenSkippedCount }}
                </el-tag>
              </template>
            </div>
            <div class="timeline-body">
              <div class="info-grid">
                <InfoItem label="尝试次数" :value="formatNullableNumber(data.attemptCount)" />
                <InfoItem label="重试次数" :value="formatNullableNumber(data.retryCount)" :highlight="(data.retryCount ?? 0) > 0" />
                <InfoItem label="Failover 次数" :value="formatNullableNumber(data.failoverCount)" :highlight="(data.failoverCount ?? 0) > 0" />
                <InfoItem label="熔断跳过次数" :value="formatNullableNumber(data.circuitOpenSkippedCount)" :highlight="(data.circuitOpenSkippedCount ?? 0) > 0" />
                <InfoItem label="上游 HTTP 状态码" :value="formatNullableNumber(data.upstreamHttpStatus)" />
                <InfoItem label="上游错误类型" :value="data.upstreamErrorType" />
                <InfoItem label="最终提供商" :value="data.providerCode" />
                <InfoItem label="提供商类型" :value="providerLabel(data.providerType)" />
              </div>
            </div>
          </div>
        </div>

        <!-- 阶段5：响应 -->
        <div class="timeline-node" :class="['timeline-node--' + getStageStatus('response')]">
          <div class="timeline-dot">
            <el-icon><CircleCheck v-if="data.status === 'SUCCESS'" /><CircleClose v-else /></el-icon>
          </div>
          <div class="timeline-content">
            <div class="timeline-title">
              响应阶段
              <el-tag :type="statusTagType(data.status)" size="small" effect="dark" class="stage-tag">
                {{ statusLabel(data.status) }}
              </el-tag>
            </div>
            <div class="timeline-body">
              <div class="info-grid">
                <InfoItem label="最终状态" :value="statusLabel(data.status)" :highlight="isErrorStatus(data.status)" />
                <InfoItem label="终止阶段" :value="terminalStageLabel(data.terminalStage)" />
                <InfoItem label="错误码" :value="data.errorCode" :highlight="Boolean(data.errorCode)" />
                <InfoItem label="错误详情" :value="data.errorMessage" :highlight="Boolean(data.errorMessage)" />
                <InfoItem label="总耗时" :value="formatNullableDuration(data.durationMs)" />
              </div>
              <div v-if="data.totalTokens != null" class="token-section">
                <div class="token-section__title">Token 用量</div>
                <div class="token-bar">
                  <div class="token-bar__item token-bar__input">
                    <div class="token-bar__value">{{ data.promptTokens ?? 0 }}</div>
                    <div class="token-bar__label">输入</div>
                  </div>
                  <div v-if="(data.cachedInputTokens ?? 0) > 0" class="token-bar__item token-bar__cached">
                    <div class="token-bar__value">{{ data.cachedInputTokens }}</div>
                    <div class="token-bar__label">缓存命中</div>
                  </div>
                  <div class="token-bar__item token-bar__output">
                    <div class="token-bar__value">{{ data.completionTokens ?? 0 }}</div>
                    <div class="token-bar__label">输出</div>
                  </div>
                  <div class="token-bar__item token-bar__total">
                    <div class="token-bar__value">{{ data.totalTokens }}</div>
                    <div class="token-bar__label">总计</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
    <template v-else>
      <el-empty description="暂无详情数据" />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Key, Share, Promotion, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import InfoItem from './InfoItem.vue'
import {
  providerLabel,
  statusLabel,
  statusTagType,
  terminalStageLabel,
  formatDuration,
  formatNullableDuration,
  formatTime,
  formatNullableNumber,
  booleanLabel,
  maskIp,
  isErrorStatus,
  hasGovernanceSignals,
} from '../../utils/request-log'
import type { RequestLogRsp } from '../../types/request-log'

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  data: RequestLogRsp | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

/**
 * 计算整体请求状态类型（用于头部标签）
 */
const overallStatusType = computed(() => {
  if (!props.data) return 'info'
  const status = props.data.status
  if (status === 'SUCCESS') {
    // 有重试或failover但最终成功，显示警告色
    if ((props.data.retryCount ?? 0) > 0 || (props.data.failoverCount ?? 0) > 0) {
      return 'warning'
    }
    return 'success'
  }
  return statusTagType(status)
})

/**
 * 计算整体请求状态标签文本
 */
const overallStatusLabel = computed(() => {
  if (!props.data) return '-'
  const status = props.data.status
  if (status === 'SUCCESS') {
    if ((props.data.retryCount ?? 0) > 0 || (props.data.failoverCount ?? 0) > 0) {
      return '成功（有重试）'
    }
    return '成功'
  }
  return statusLabel(status)
})

/**
 * 计算鉴权阶段状态标签
 */
const authStatusLabel = computed(() => {
  if (!props.data) return '-'
  // 如果请求在鉴权阶段就被终止，显示失败
  if (props.data.terminalStage === 'AUTH' || props.data.terminalStage === 'RATE_LIMIT') {
    return '失败'
  }
  // 如果整体成功，鉴权也视为成功
  if (props.data.status === 'SUCCESS') {
    return '成功'
  }
  return '通过'
})

/**
 * 获取每个阶段的状态样式
 * @param stage 阶段名称
 * @returns 状态类型：success / warning / error / skipped / pending
 */
function getStageStatus(stage: string): string {
  if (!props.data) return 'pending'
  const { status, terminalStage, retryCount, failoverCount, circuitOpenSkippedCount, rateLimitTriggered } = props.data

  switch (stage) {
    case 'overview':
      // 请求概况始终显示为中性
      return 'pending'

    case 'auth':
      if (terminalStage === 'AUTH' || terminalStage === 'RATE_LIMIT') {
        return 'error'
      }
      if (rateLimitTriggered) {
        return 'warning'
      }
      return 'success'

    case 'routing':
      if (terminalStage === 'ROUTING') {
        return 'error'
      }
      return 'success'

    case 'upstream': {
      // 如果终止阶段在调用之前，此阶段未执行
      if (terminalStage === 'AUTH' || terminalStage === 'RATE_LIMIT' || terminalStage === 'ROUTING') {
        return 'skipped'
      }
      // 有熔断跳过
      if ((circuitOpenSkippedCount ?? 0) > 0) {
        return 'skipped'
      }
      if (terminalStage === 'FAILOVER') {
        return 'warning'
      }
      // 有重试或failover
      if ((retryCount ?? 0) > 0 || (failoverCount ?? 0) > 0) {
        return status === 'SUCCESS' ? 'warning' : 'error'
      }
      return status === 'SUCCESS' ? 'success' : 'error'
    }

    case 'response':
      if (status === 'SUCCESS') {
        if ((retryCount ?? 0) > 0 || (failoverCount ?? 0) > 0) {
          return 'warning'
        }
        return 'success'
      }
      if (status === 'CANCELLED') {
        return 'warning'
      }
      return 'error'

    default:
      return 'pending'
  }
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.detail-loading {
  padding: 8px 0;
}

/* 头部区域 */
.trace-header {
  padding: 16px;
  background: var(--el-bg-color-page);
  border-radius: 8px;
  margin-bottom: 20px;
  border: 1px solid var(--el-border-color-light);
}

.trace-header__id {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.trace-header__label {
  font-size: 12px;
  color: var(--text-secondary);
}

.trace-header__value {
  font-family: monospace;
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-all;
}

.trace-header__copy {
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 0;
  font-size: 12px;
}

.trace-header__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.trace-header__duration {
  font-size: 13px;
  color: var(--text-primary);
  font-weight: 500;
}

.trace-header__time {
  font-size: 12px;
  color: var(--text-secondary);
}

/* 时间线容器 */
.trace-timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
  padding-left: 8px;
}

/* 时间线节点 */
.timeline-node {
  display: flex;
  gap: 12px;
  padding-bottom: 20px;
  position: relative;
}

/* 节点之间的连接线 */
.timeline-node::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 32px;
  bottom: 0;
  width: 2px;
  background: var(--el-border-color);
}

.timeline-node:last-child::before {
  display: none;
}

/* 状态颜色定义 */
.timeline-node--success .timeline-dot {
  background: var(--el-color-success);
  color: #fff;
}

.timeline-node--success .timeline-title {
  color: var(--el-color-success);
}

.timeline-node--warning .timeline-dot {
  background: var(--el-color-warning);
  color: #fff;
}

.timeline-node--warning .timeline-title {
  color: var(--el-color-warning);
}

.timeline-node--error .timeline-dot {
  background: var(--el-color-danger);
  color: #fff;
}

.timeline-node--error .timeline-title {
  color: var(--el-color-danger);
}

.timeline-node--skipped .timeline-dot {
  background: var(--el-text-color-disabled);
  color: #fff;
}

.timeline-node--skipped .timeline-title {
  color: var(--el-text-color-disabled);
}

.timeline-node--pending .timeline-dot {
  background: var(--el-color-info);
  color: #fff;
}

.timeline-node--pending .timeline-title {
  color: var(--text-primary);
}

/* 圆点 */
.timeline-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
  background: var(--el-border-color);
  color: var(--text-secondary);
  transition: background 0.2s, color 0.2s;
  z-index: 1;
}

/* 内容区 */
.timeline-content {
  flex: 1;
  min-width: 0;
  padding-top: 4px;
}

.timeline-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.stage-tag {
  font-size: 11px;
}

.timeline-body {
  background: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 12px;
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 600px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}

/* Token 统计区域 */
.token-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-light);
}

.token-section__title {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.token-bar {
  display: flex;
  gap: 8px;
}

.token-bar__item {
  flex: 1;
  text-align: center;
  padding: 8px 4px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.token-bar__input {
  border-top: 3px solid var(--el-color-primary);
}

.token-bar__cached {
  border-top: 3px solid var(--el-color-success);
}

.token-bar__output {
  border-top: 3px solid var(--el-color-warning);
}

.token-bar__total {
  border-top: 3px solid var(--el-color-info);
}

.token-bar__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.token-bar__label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}
</style>
