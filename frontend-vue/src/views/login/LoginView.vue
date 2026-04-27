<template>
  <div class="login-page" @mousemove="onMouseMove" @mouseleave="onMouseLeave">
    <!-- 星链背景画布 -->
    <canvas ref="canvasRef" class="login-canvas" />

    <!-- 登录 / 初始化卡片 -->
    <div class="login-card">
      <!-- 品牌区 -->
      <div class="login-brand">
        <div class="brand-header">
          <div class="brand-icon">
            <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
              <rect x="2" y="2" width="28" height="28" rx="8" stroke="currentColor" stroke-width="2" />
              <path d="M10 16L14 20L22 12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </div>
          <div class="mode-pill">{{ modeLabel }}</div>
        </div>
        <h1 class="brand-title">AI Gateway</h1>
        <p class="brand-subtitle">{{ subtitle }}</p>
      </div>

      <div v-if="!authStore.ready" class="login-loading">
        <el-icon class="login-loading__icon"><Loading /></el-icon>
        <span>正在检查系统状态...</span>
      </div>

      <template v-else>
        <div v-if="authStore.bootstrapError" class="status-warning">
          <div class="status-warning__title">系统状态检查失败</div>
          <div class="status-warning__desc">{{ authStore.bootstrapError }}</div>
          <div class="status-warning__actions">
            <el-button size="small" :loading="retryingStatus" @click="retryBootstrap">
              {{ retryingStatus ? '重试中...' : '重新检测' }}
            </el-button>
          </div>
        </div>

        <div v-if="isSetupMode" class="setup-tip">
          <el-icon class="setup-tip__icon"><Lock /></el-icon>
          <div class="setup-tip__content">
            <div class="setup-tip__title">安全提醒</div>
            <div class="setup-tip__desc">这是系统首次启动，请设置管理员凭证。初始化后系统将进入单用户模式，当前账户将拥有唯一管理权限。</div>
          </div>
        </div>

        <!-- 表单区 -->
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          class="login-form"
          @submit.prevent="handleSubmit"
        >
          <el-form-item prop="username">
            <el-input
              v-model.trim="form.username"
              placeholder="用户名"
              size="large"
              :prefix-icon="User"
              maxlength="32"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              maxlength="64"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <el-form-item v-if="isSetupMode" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="确认密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              maxlength="64"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <div v-if="isSetupMode" class="password-hints">
            <span class="password-hints__item" :class="{ 'is-valid': form.password.length >= 8 }">
              <el-icon v-if="form.password.length >= 8"><Check /></el-icon>
              <span v-else class="hint-dot"></span>
              至少 8 位
            </span>
            <span class="password-hints__item" :class="{ 'is-valid': /[A-Za-z]/.test(form.password) }">
              <el-icon v-if="/[A-Za-z]/.test(form.password)"><Check /></el-icon>
              <span v-else class="hint-dot"></span>
              包含字母
            </span>
            <span class="password-hints__item" :class="{ 'is-valid': /\d/.test(form.password) }">
              <el-icon v-if="/\d/.test(form.password)"><Check /></el-icon>
              <span v-else class="hint-dot"></span>
              包含数字
            </span>
          </div>

          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ actionText }}
          </el-button>
        </el-form>

        <!-- 底部信息 -->
        <div class="login-footer">
          <span>AI Gateway &copy; {{ new Date().getFullYear() }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Loading, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, FormItemRule } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const retryingStatus = ref(false)
const canvasRef = ref<HTMLCanvasElement | null>(null)
let animFrameId = 0

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const isSetupMode = computed(() => authStore.initialized === false)
const modeLabel = computed(() => (isSetupMode.value ? '首次初始化' : '安全登录'))
const subtitle = computed(() => (
  isSetupMode.value
    ? '初始化系统并配置管理员'
    : '使用管理员账户继续访问管理控制台'
))
const actionText = computed(() => (
  loading.value
    ? (isSetupMode.value ? '创建中...' : '登录中...')
    : (isSetupMode.value ? '创建管理员并进入系统' : '登录')
))

const passwordRule: FormItemRule = {
  validator: (_rule, value: string, callback) => {
    if (!value) {
      callback(new Error('请输入密码'))
      return
    }
    if (isSetupMode.value) {
      if (value.length < 8) {
        callback(new Error('密码至少 8 位'))
        return
      }
      if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
        callback(new Error('密码需包含字母和数字'))
        return
      }
      if (form.confirmPassword) {
        formRef.value?.validateField('confirmPassword')
      }
    }
    callback()
  },
  trigger: 'blur',
}

const confirmPasswordRule: FormItemRule = {
  validator: (_rule, value: string, callback) => {
    if (!isSetupMode.value) {
      callback()
      return
    }
    if (!value) {
      callback(new Error('请再次输入密码'))
      return
    }
    if (value !== form.password) {
      callback(new Error('两次输入密码不一致'))
      return
    }
    callback()
  },
  trigger: 'blur',
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value) {
          callback()
          return
        }
        if (value.length < 3 || value.length > 32) {
          callback(new Error('用户名长度需为 3 到 32 位'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  password: [passwordRule],
  confirmPassword: [confirmPasswordRule],
}

async function handleSubmit() {
  if (!formRef.value || !authStore.ready) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      if (isSetupMode.value) {
        await authStore.initializeAdmin({
          username: form.username,
          password: form.password,
        })
        ElMessage.success('管理员创建成功，已自动登录')
        router.replace('/dashboard')
        return
      }

      await authStore.login({ username: form.username, password: form.password })
      ElMessage.success('登录成功')
      const redirect = (router.currentRoute.value.query.redirect as string) || '/dashboard'
      router.replace(redirect)
    } catch {
      // 错误信息已由 request 拦截器展示
    } finally {
      loading.value = false
    }
  })
}

async function retryBootstrap() {
  retryingStatus.value = true
  try {
    await authStore.bootstrap(true)
    if (authStore.initialized && authStore.isAuthenticated) {
      router.replace('/dashboard')
      return
    }
    ElMessage.success('系统状态已刷新')
  } catch {
    // 错误信息已由 request 拦截器展示
  } finally {
    retryingStatus.value = false
  }
}

// ==================== 星链交互背景 ====================

interface Star {
  x: number
  y: number
  vx: number
  vy: number
  baseRadius: number
  /** 闪烁相位，让每颗星呼吸节奏不同 */
  phase: number
  /** 当前亮度 [0,1] */
  brightness: number
}

/** 鼠标状态 */
const mouse = ref({ x: -9999, y: -9999, active: false })

function onMouseMove(e: MouseEvent) {
  mouse.value = { x: e.clientX, y: e.clientY, active: true }
}

function onMouseLeave() {
  mouse.value.active = false
}

function initCanvas() {
  const canvasEl = canvasRef.value
  if (!canvasEl) return

  const context = canvasEl.getContext('2d')
  if (!context) return

  const canvas = canvasEl
  const ctx = context

  let width = 0
  let height = 0
  let stars: Star[] = []

  // 配置参数
  const STAR_DENSITY = 0.00012          // 每像素星星数
  const MAX_LINK_DIST = 150             // 星星间最大连线距离
  const MOUSE_RADIUS = 200              // 鼠标影响半径
  const MOUSE_ATTRACT = 0.015           // 鼠标吸引力强度
  const MOUSE_REPEL_DIST = 80           // 鼠标排斥半径（太近时推开）
  const MOUSE_REPEL = 0.05              // 排斥力强度
  const FRICTION = 0.98                 // 速度衰减（模拟摩擦）
  const BASE_SPEED = 0.25               // 星星基础移动速度
  const PULSE_SPEED = 0.02              // 闪烁频率

  // 颜色主题：蓝色 + 青色星链，偶尔暖色星点
  const COLORS = [
    { r: 67, g: 97, b: 238 },   // 主蓝
    { r: 105, g: 131, b: 242 },  // 亮蓝
    { r: 6, g: 182, b: 212 },    // 青
    { r: 140, g: 160, b: 242 },  // 淡紫蓝
    { r: 200, g: 160, b: 100 },  // 暖金（少量）
  ]

  function resize() {
    width = canvas.width = window.innerWidth
    height = canvas.height = window.innerHeight
    rebuildStars()
  }

  function rebuildStars() {
    // 根据屏幕面积自适应数量
    const count = Math.floor(width * height * STAR_DENSITY)
    const clamped = Math.max(40, Math.min(count, 200))
    stars = []
    for (let i = 0; i < clamped; i += 1) {
      stars.push(createStar())
    }
  }

  function createStar(): Star {
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * BASE_SPEED * 2,
      vy: (Math.random() - 0.5) * BASE_SPEED * 2,
      baseRadius: 1 + Math.random() * 2,
      phase: Math.random() * Math.PI * 2,
      brightness: 0.4 + Math.random() * 0.6,
    }
  }

  function update() {
    for (const s of stars) {
      // 闪烁呼吸
      s.phase += PULSE_SPEED
      s.brightness = 0.3 + 0.7 * ((Math.sin(s.phase) + 1) / 2)

      // 鼠标交互：吸引 + 近距排斥
      if (mouse.value.active) {
        const dx = mouse.value.x - s.x
        const dy = mouse.value.y - s.y
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < MOUSE_RADIUS && dist > 0.1) {
          if (dist > MOUSE_REPEL_DIST) {
            // 吸引
            const force = MOUSE_ATTRACT * (1 - dist / MOUSE_RADIUS)
            s.vx += (dx / dist) * force
            s.vy += (dy / dist) * force
          } else {
            // 排斥（避免星点堆在鼠标中心）
            const force = MOUSE_REPEL * (1 - dist / MOUSE_REPEL_DIST)
            s.vx -= (dx / dist) * force
            s.vy -= (dy / dist) * force
          }
        }
      }

      // 摩擦衰减
      s.vx *= FRICTION
      s.vy *= FRICTION

      // 速度限制
      const speed = Math.sqrt(s.vx * s.vx + s.vy * s.vy)
      if (speed < BASE_SPEED * 0.3) {
        // 太慢时给一点随机推力，避免画面静止
        s.vx += (Math.random() - 0.5) * 0.05
        s.vy += (Math.random() - 0.5) * 0.05
      } else if (speed > 3) {
        s.vx = (s.vx / speed) * 3
        s.vy = (s.vy / speed) * 3
      }

      // 更新位置
      s.x += s.vx
      s.y += s.vy

      // 边界处理：软回弹，避免硬跳变
      if (s.x < -20) s.x = width + 20
      if (s.x > width + 20) s.x = -20
      if (s.y < -20) s.y = height + 20
      if (s.y > height + 20) s.y = -20
    }
  }

  function draw() {
    ctx.clearRect(0, 0, width, height)

    // ---- 画星链连线 ----
    for (let i = 0; i < stars.length; i += 1) {
      for (let j = i + 1; j < stars.length; j += 1) {
        const dx = stars[i].x - stars[j].x
        const dy = stars[i].y - stars[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MAX_LINK_DIST) {
          const alpha = (1 - dist / MAX_LINK_DIST) * 0.25
          // 连线亮度取两颗星中较亮的那个
          const b = Math.max(stars[i].brightness, stars[j].brightness)
          const c = pickLineColor(i)
          ctx.strokeStyle = `rgba(${c.r},${c.g},${c.b},${alpha * b})`
          ctx.lineWidth = (1 - dist / MAX_LINK_DIST) * 1.5
          ctx.beginPath()
          ctx.moveTo(stars[i].x, stars[i].y)
          ctx.lineTo(stars[j].x, stars[j].y)
          ctx.stroke()
        }
      }
    }

    // ---- 画鼠标与附近星星的连线 ----
    if (mouse.value.active) {
      for (const s of stars) {
        const dx = mouse.value.x - s.x
        const dy = mouse.value.y - s.y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MOUSE_RADIUS) {
          const alpha = (1 - dist / MOUSE_RADIUS) * 0.4
          ctx.strokeStyle = `rgba(105,131,242,${alpha})`
          ctx.lineWidth = (1 - dist / MOUSE_RADIUS) * 2
          ctx.beginPath()
          ctx.moveTo(mouse.value.x, mouse.value.y)
          ctx.lineTo(s.x, s.y)
          ctx.stroke()
        }
      }
    }

    // ---- 画星星 ----
    for (const s of stars) {
      const radius = s.baseRadius * s.brightness
      const c = starColor(s)

      // 外发光
      const glow = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, radius * 4)
      glow.addColorStop(0, `rgba(${c.r},${c.g},${c.b},${0.3 * s.brightness})`)
      glow.addColorStop(1, `rgba(${c.r},${c.g},${c.b},0)`)
      ctx.fillStyle = glow
      ctx.beginPath()
      ctx.arc(s.x, s.y, radius * 4, 0, Math.PI * 2)
      ctx.fill()

      // 星点核心
      ctx.fillStyle = `rgba(${c.r},${c.g},${c.b},${s.brightness})`
      ctx.beginPath()
      ctx.arc(s.x, s.y, radius, 0, Math.PI * 2)
      ctx.fill()
    }

    // ---- 鼠标光标光晕 ----
    if (mouse.value.active) {
      const cursorGlow = ctx.createRadialGradient(
        mouse.value.x, mouse.value.y, 0,
        mouse.value.x, mouse.value.y, MOUSE_RADIUS * 0.5,
      )
      cursorGlow.addColorStop(0, 'rgba(67,97,238,0.06)')
      cursorGlow.addColorStop(0.5, 'rgba(67,97,238,0.02)')
      cursorGlow.addColorStop(1, 'rgba(67,97,238,0)')
      ctx.fillStyle = cursorGlow
      ctx.beginPath()
      ctx.arc(mouse.value.x, mouse.value.y, MOUSE_RADIUS * 0.5, 0, Math.PI * 2)
      ctx.fill()
    }

    update()
    animFrameId = requestAnimationFrame(draw)
  }

  // 基于索引选连线颜色（确定性，避免每帧随机闪烁）
  function pickLineColor(index: number) {
    return COLORS[index % 4]
  }

  // 基于相位选星星颜色（确定性）
  function starColor(s: Star) {
    const idx = Math.floor(s.phase / (Math.PI * 2) * COLORS.length) % COLORS.length
    return COLORS[idx]
  }

  resize()
  draw()
  window.addEventListener('resize', resize)
}

onMounted(async () => {
  initCanvas()
  if (!authStore.ready) {
    try {
      await authStore.bootstrap()
    } catch {
      // 忽略首次状态探测异常，页面会降级为可手动登录并支持重试
    }
  }
  if (authStore.initialized && authStore.isAuthenticated) {
    router.replace('/dashboard')
  }
})

onUnmounted(() => {
  cancelAnimationFrame(animFrameId)
})
</script>

<style scoped>
.login-page {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0b1a;
  overflow: hidden;
}

.login-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* 登录卡片 — 玻璃态 */
.login-card {
  position: relative;
  z-index: 1;
  width: 420px;
  padding: 40px 36px 32px;
  background: rgba(29, 30, 44, 0.85);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow:
    0 32px 64px rgba(0, 0, 0, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset,
    0 1px 0 rgba(255, 255, 255, 0.06) inset;
}

/* 品牌区 */
.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.brand-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
}

.brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4361ee 0%, #3451b2 100%);
  color: #fff;
  box-shadow: 0 6px 18px rgba(67, 97, 238, 0.3);
}

.mode-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 88px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(67, 97, 238, 0.14);
  color: #a9b8ff;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.brand-title {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.01em;
  margin: 0 0 4px;
}

.brand-subtitle {
  font-size: 13px;
  color: rgba(163, 166, 183, 0.8);
  margin: 0;
}

.login-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 160px;
  color: rgba(226, 232, 240, 0.82);
}

.login-loading__icon {
  font-size: 18px;
  animation: rotating 1s linear infinite;
}

.status-warning {
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(245, 158, 11, 0.1);
  border: 1px solid rgba(245, 158, 11, 0.22);
}

.status-warning__title {
  font-size: 13px;
  font-weight: 600;
  color: #fde68a;
}

.status-warning__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(253, 230, 138, 0.86);
}

.status-warning__actions {
  margin-top: 12px;
}

.setup-tip {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(145deg, rgba(67, 97, 238, 0.12), rgba(67, 97, 238, 0.04));
  border: 1px solid rgba(67, 97, 238, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.setup-tip__icon {
  font-size: 20px;
  color: #6983f2;
  margin-top: 2px;
}

.setup-tip__content {
  flex: 1;
}

.setup-tip__title {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.setup-tip__desc {
  font-size: 12px;
  line-height: 1.6;
  color: rgba(191, 201, 232, 0.85);
}

.password-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: -4px 0 16px;
}

.password-hints__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 12px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(196, 200, 216, 0.7);
  font-size: 12px;
  transition: all 0.25s ease;
}

.password-hints__item.is-valid {
  background: rgba(16, 185, 129, 0.1);
  border-color: rgba(16, 185, 129, 0.3);
  color: #34d399;
}

.hint-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.5;
}

.password-hints__item .el-icon {
  font-size: 14px;
}

/* 表单 — Element Plus 暗色覆盖 */
.login-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  box-shadow: none !important;
  transition: border-color 0.2s, background 0.2s;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: rgba(67, 97, 238, 0.4);
  background: rgba(255, 255, 255, 0.08);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: #4361ee;
  background: rgba(255, 255, 255, 0.1);
}

.login-form :deep(.el-input__inner) {
  color: #e0e0e8;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: rgba(163, 166, 183, 0.6);
}

.login-form :deep(.el-form-item__error) {
  color: #f56c6c;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  margin-top: 4px;
  background: linear-gradient(135deg, #4361ee, #3451b2);
  border: none;
  letter-spacing: 0.02em;
}

.login-btn:hover {
  background: linear-gradient(135deg, #5575f0, #3f5ec2);
}

/* 底部 */
.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 12px;
  color: rgba(163, 166, 183, 0.4);
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式 */
@media (max-width: 480px) {
  .login-card {
    width: calc(100% - 32px);
    padding: 32px 24px 24px;
  }

  .password-hints {
    gap: 6px;
  }
}
</style>
