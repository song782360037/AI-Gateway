<template>
  <div class="admin-layout">
    <!-- 左侧深色侧边栏 -->
    <aside class="sidebar">
      <!-- Logo 品牌 -->
      <div class="sidebar-logo">
        <div class="sidebar-logo__mark">
          <!-- 棱镜分流图标：请求 → 棱镜 → 多路输出 -->
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <!-- 输入信号线 -->
            <path d="M0 9H5" stroke="white" stroke-width="2" stroke-linecap="round" opacity="0.7"/>
            <!-- 棱镜三角形（半透明填充 + 描边） -->
            <path d="M6 3L14 9L6 15Z" fill="white" fill-opacity="0.3"/>
            <path d="M6 3L14 9L6 15Z" stroke="white" stroke-width="1.5" stroke-linejoin="round"/>
            <!-- 输出分流线（上/中/下三路，递减透明度） -->
            <path d="M14 9L18 4.5" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.9"/>
            <path d="M14 9H18" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.6"/>
            <path d="M14 9L18 13.5" stroke="white" stroke-width="1.5" stroke-linecap="round" opacity="0.35"/>
          </svg>
        </div>
        <div class="sidebar-logo__text">
          <h1>AI Gateway</h1>
          <span class="sidebar-logo__sub">Console</span>
        </div>
      </div>

      <!-- 导航菜单 -->
      <el-menu
        :default-active="activeMenu"
        :router="true"
        background-color="transparent"
        text-color="var(--sidebar-text)"
        active-text-color="var(--sidebar-active-text)"
        class="sidebar-menu"
      >
        <div class="sidebar-nav-label">概览</div>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/runtime">
          <el-icon><Odometer /></el-icon>
          <span>运行时状态</span>
        </el-menu-item>

        <div class="sidebar-nav-label">配置</div>
        <el-menu-item index="/provider">
          <el-icon><Connection /></el-icon>
          <span>提供商管理</span>
        </el-menu-item>
        <el-menu-item index="/api-key">
          <el-icon><Key /></el-icon>
          <span>API Key 配置</span>
        </el-menu-item>

        <div class="sidebar-nav-label">监控</div>
        <el-menu-item index="/request-log">
          <el-icon><Document /></el-icon>
          <span>请求日志</span>
        </el-menu-item>
      </el-menu>

      <!-- 侧边栏底部：用户信息 + 账号操作 -->
      <div class="sidebar-footer">
        <button class="sidebar-user sidebar-user--button" @click="openAccountDialog('username')">
          <div class="sidebar-user__avatar">
            <el-icon :size="14"><User /></el-icon>
          </div>
          <div class="sidebar-user__content">
            <span class="sidebar-user__name">{{ username }}</span>
            <span class="sidebar-user__meta">账号设置</span>
          </div>
        </button>
        <div class="sidebar-actions">
          <el-tooltip content="账号设置" placement="right">
            <button class="sidebar-action" @click="openAccountDialog('username')">
              <el-icon :size="16"><Setting /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="退出登录" placement="right">
            <button class="sidebar-action sidebar-action--danger" @click="handleLogout">
              <el-icon :size="16"><SwitchButton /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </div>
    </aside>

    <!-- 右侧主内容区 -->
    <div class="main-wrapper">
      <header class="topbar">
        <h2 class="topbar-title">{{ pageTitle }}</h2>
      </header>
      <main class="content">
        <slot></slot>
      </main>
    </div>
  </div>

  <el-dialog
    v-model="accountDialogVisible"
    title="账号设置"
    width="520px"
    destroy-on-close
    class="account-dialog"
    @closed="handleDialogClosed"
  >
    <div class="account-dialog__intro">
      <div class="account-dialog__intro-title">当前管理员：{{ username }}</div>
      <div class="account-dialog__intro-desc">修改密码后将自动轮换当前会话，并使其他已登录设备失效。</div>
    </div>

    <el-tabs v-model="activeTab" class="account-tabs">
      <el-tab-pane label="修改用户名" name="username">
        <el-form
          ref="usernameFormRef"
          :model="usernameForm"
          :rules="usernameRules"
          label-position="top"
          @submit.prevent="submitUsernameChange"
        >
          <el-form-item label="当前用户名">
            <el-input :model-value="username" disabled />
          </el-form-item>
          <el-form-item label="新用户名" prop="newUsername">
            <el-input v-model.trim="usernameForm.newUsername" maxlength="32" placeholder="请输入新的管理员用户名" />
          </el-form-item>
          <el-form-item label="当前密码" prop="currentPassword">
            <el-input
              v-model="usernameForm.currentPassword"
              type="password"
              show-password
              maxlength="64"
              placeholder="请输入当前密码以确认修改"
              @keyup.enter="submitUsernameChange"
            />
          </el-form-item>
          <div class="account-actions">
            <el-button @click="accountDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="updatingUsername" @click="submitUsernameChange">
              {{ updatingUsername ? '保存中...' : '保存用户名' }}
            </el-button>
          </div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          @submit.prevent="submitPasswordChange"
        >
          <el-form-item label="当前密码" prop="currentPassword">
            <el-input
              v-model="passwordForm.currentPassword"
              type="password"
              show-password
              maxlength="64"
              placeholder="请输入当前密码"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              show-password
              maxlength="64"
              placeholder="至少 8 位，且包含字母和数字"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              show-password
              maxlength="64"
              placeholder="请再次输入新密码"
              @keyup.enter="submitPasswordChange"
            />
          </el-form-item>
          <div class="password-hints">
            <span class="password-hints__item">至少 8 位</span>
            <span class="password-hints__item">包含字母</span>
            <span class="password-hints__item">包含数字</span>
            <span class="password-hints__item">自动安全轮换会话</span>
          </div>
          <div class="account-actions">
            <el-button @click="accountDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="updatingPassword" @click="submitPasswordChange">
              {{ updatingPassword ? '更新中...' : '更新密码' }}
            </el-button>
          </div>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, DataAnalysis, Document, Key, Odometer, Setting, SwitchButton, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, FormItemRule } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const meta = route.meta ?? {}
  return typeof meta.title === 'string' ? meta.title : ''
})

const username = computed(() => authStore.username || '管理员')

const accountDialogVisible = ref(false)
const activeTab = ref<'username' | 'password'>('username')
const updatingUsername = ref(false)
const updatingPassword = ref(false)
const usernameFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const usernameForm = reactive({
  newUsername: '',
  currentPassword: '',
})

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordStrengthRule: FormItemRule = {
  validator: (_rule, value: string, callback) => {
    if (!value) {
      callback(new Error('请输入新密码'))
      return
    }
    if (value.length < 8 || value.length > 64) {
      callback(new Error('密码长度需为 8 到 64 位'))
      return
    }
    if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
      callback(new Error('密码至少包含 1 个字母和 1 个数字'))
      return
    }
    if (value === passwordForm.currentPassword) {
      callback(new Error('新密码不能与当前密码相同'))
      return
    }
    if (passwordForm.confirmPassword) {
      passwordFormRef.value?.validateField('confirmPassword')
    }
    callback()
  },
  trigger: 'blur',
}

const confirmPasswordRule: FormItemRule = {
  validator: (_rule, value: string, callback) => {
    if (!value) {
      callback(new Error('请再次输入新密码'))
      return
    }
    if (value !== passwordForm.newPassword) {
      callback(new Error('两次输入的新密码不一致'))
      return
    }
    callback()
  },
  trigger: 'blur',
}

const usernameRules: FormRules = {
  newUsername: [
    { required: true, message: '请输入新用户名', trigger: 'blur' },
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
        if (value === username.value) {
          callback(new Error('新用户名不能与当前用户名相同'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
}

const passwordRules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [passwordStrengthRule],
  confirmPassword: [confirmPasswordRule],
}

function resetUsernameForm() {
  usernameForm.newUsername = ''
  usernameForm.currentPassword = ''
  usernameFormRef.value?.clearValidate()
}

function resetPasswordForm() {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordFormRef.value?.clearValidate()
}

function openAccountDialog(tab: 'username' | 'password') {
  activeTab.value = tab
  accountDialogVisible.value = true
  usernameForm.newUsername = username.value
  nextTick(() => {
    if (tab === 'username') {
      passwordFormRef.value?.clearValidate()
    } else {
      usernameFormRef.value?.clearValidate()
    }
  })
}

function handleDialogClosed() {
  resetUsernameForm()
  resetPasswordForm()
  activeTab.value = 'username'
}

async function submitUsernameChange() {
  if (!usernameFormRef.value) return

  await usernameFormRef.value.validate(async (valid) => {
    if (!valid) return

    updatingUsername.value = true
    try {
      await authStore.updateUsername({
        currentPassword: usernameForm.currentPassword,
        newUsername: usernameForm.newUsername,
      })
      ElMessage.success('用户名修改成功')
      accountDialogVisible.value = false
    } catch {
      // 错误信息由请求拦截器统一处理
    } finally {
      updatingUsername.value = false
    }
  })
}

async function submitPasswordChange() {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    updatingPassword.value = true
    try {
      await authStore.changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      })
      ElMessage.success('密码修改成功，当前会话已安全刷新')
      accountDialogVisible.value = false
    } catch {
      // 错误信息由请求拦截器统一处理
    } finally {
      updatingPassword.value = false
    }
  })
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await authStore.logout()
    router.replace('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
/* 侧边栏底部用户区 */
.sidebar-footer {
  margin-top: auto;
  padding: 14px 16px;
  border-top: 1px solid var(--sidebar-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--sidebar-text);
}

.sidebar-user--button {
  flex: 1;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.sidebar-user--button:hover .sidebar-user__name {
  color: #ffffff;
}

.sidebar-user__content {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 用户头像占位圆 */
.sidebar-user__avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.12), rgba(6, 182, 212, 0.08));
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sidebar-user__avatar .el-icon {
  color: var(--color-primary);
  font-size: 14px;
}

.sidebar-user__name {
  font-size: 13px;
  font-weight: 450;
  color: var(--text-regular);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-user__meta {
  margin-top: 2px;
  font-size: 11px;
  color: rgba(163, 166, 183, 0.68);
}

.sidebar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--sidebar-text);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.sidebar-action:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.sidebar-action--danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #f87171;
}

.account-dialog__intro {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(67, 97, 238, 0.08), rgba(6, 182, 212, 0.06));
}

.account-dialog__intro-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.account-dialog__intro-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.account-tabs {
  margin-top: 8px;
}

.account-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 18px;
}

.password-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.password-hints__item {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}
</style>
