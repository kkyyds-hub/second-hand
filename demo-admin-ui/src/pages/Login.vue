<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ShieldCheck, Activity, Users, Lock } from 'lucide-vue-next'
import { login } from '@/api/auth'
import { saveAdminToken } from '@/utils/request'

const router = useRouter()

// 登录表单数据
const loginForm = ref({
  loginId: '',
  password: '',
  remember: false
})

// 页面状态
const loading = ref(false)
const errorMessage = ref('')
const showForgotModal = ref(false)

/**
 * 处理登录提交
 */
const handleLogin = async () => {
  // 基础校验
  if (!loginForm.value.loginId || !loginForm.value.password) {
    errorMessage.value = '请输入账号和密码'
    return
  }

  try {
    loading.value = true
    errorMessage.value = ''
    
    // 调用登录接口
    const res = await login({
      loginId: loginForm.value.loginId,
      password: loginForm.value.password
    })
    
    // 登录成功，保存 token 并跳转
    if (res && res.token) {
      // 复用 request.ts 中的 token 存储逻辑
      saveAdminToken(res.token)
      // 跳转到首页
      router.push('/')
    } else {
      errorMessage.value = '登录失败，未获取到有效凭证'
    }
  } catch (error: any) {
    // 捕获并展示后端返回的错误信息或通用网络错误
    errorMessage.value = error.message || '登录失败，请检查账号密码或网络状态'
  } finally {
    // 无论成功失败，解除 loading 状态
    loading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen bg-[#fafafa] flex items-center justify-center p-4 sm:p-8 font-sans selection:bg-gray-200">
    <div class="max-w-[1000px] w-full flex flex-col lg:flex-row bg-white rounded-2xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] overflow-hidden border border-gray-200/60">
      
      <!-- 左侧：品牌与说明区 -->
      <div class="lg:w-5/12 p-10 lg:p-14 flex flex-col justify-between relative overflow-hidden bg-gray-50/50 border-r border-gray-100">
        <div class="relative z-10">
          <!-- Logo 与系统名称 -->
          <div class="flex items-center space-x-3 mb-14">
            <!-- Brand Logo: KK + Circular/Flow Concept (Synced with MainLayout) -->
            <div class="relative flex items-center justify-center shrink-0 w-10 h-10 bg-gradient-to-br from-gray-900 to-gray-800 rounded-xl shadow-md overflow-hidden">
              <!-- Subtle background flow pattern -->
              <svg class="absolute inset-0 w-full h-full opacity-20" viewBox="0 0 36 36" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M18 4C25.732 4 32 10.268 32 18C32 25.732 25.732 32 18 32C10.268 32 4 25.732 4 18" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-dasharray="4 4"/>
                <path d="M18 4L22 8M18 4L14 8" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <path d="M18 32L14 28M18 32L22 28" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <!-- KK Text -->
              <span class="relative z-10 text-white text-[16px] font-black tracking-tighter italic pr-0.5">KK</span>
            </div>
            <span class="text-lg font-semibold text-gray-900 tracking-wide">运营后台</span>
          </div>

          <!-- 核心标语 -->
          <h1 class="text-2xl font-semibold text-gray-900 mb-4 leading-snug tracking-tight">
            专业、高效的<br/>
            业务管理中枢
          </h1>
          
          <p class="text-[13px] text-gray-500 mb-12 leading-relaxed">
            为平台运营人员提供全链路的数据监控、用户管理、商品审核与风控仲裁能力，保障平台生态健康运转。
          </p>

          <!-- 系统能力说明 -->
          <div class="space-y-6 hidden sm:block">
            <div class="flex items-start space-x-4">
              <div class="bg-white p-2 rounded-lg text-gray-700 mt-0.5 border border-gray-200/60 shadow-sm">
                <Activity class="w-4 h-4" />
              </div>
              <div>
                <h3 class="text-[14px] text-gray-900 font-medium mb-1">实时大盘监控</h3>
                <p class="text-[13px] text-gray-500">核心业务指标秒级更新，异常波动智能预警</p>
              </div>
            </div>
            <div class="flex items-start space-x-4">
              <div class="bg-white p-2 rounded-lg text-gray-700 mt-0.5 border border-gray-200/60 shadow-sm">
                <ShieldCheck class="w-4 h-4" />
              </div>
              <div>
                <h3 class="text-[14px] text-gray-900 font-medium mb-1">智能风控拦截</h3>
                <p class="text-[13px] text-gray-500">多维度安全策略，自动识别并阻断高危交易</p>
              </div>
            </div>
            <div class="flex items-start space-x-4">
              <div class="bg-white p-2 rounded-lg text-gray-700 mt-0.5 border border-gray-200/60 shadow-sm">
                <Users class="w-4 h-4" />
              </div>
              <div>
                <h3 class="text-[14px] text-gray-900 font-medium mb-1">全景用户画像</h3>
                <p class="text-[13px] text-gray-500">深度洞察用户行为，精准定位违规账号</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部版权与安全提示 -->
        <div class="relative z-10 mt-12 pt-6 text-[12px] text-gray-400 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
          <span>&copy; 2026 KK Platform.</span>
          <span class="flex items-center"><Lock class="w-3 h-3 mr-1.5"/> 内部系统</span>
        </div>
      </div>

      <!-- 右侧：登录表单区 -->
      <div class="lg:w-7/12 bg-white p-10 lg:p-16 flex flex-col justify-center relative">
        <div class="max-w-[360px] w-full mx-auto">
          <!-- 登录标题 -->
          <div class="mb-10">
            <div class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md bg-gray-50 border border-gray-200/60 text-[11px] font-medium text-gray-600 mb-4">
              <span class="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
              <span>KK Management</span>
            </div>
            <h2 class="text-2xl font-bold text-gray-900 mb-2 tracking-tight">登录工作台</h2>
            <p class="text-[13px] text-gray-500">请输入您的管理账号，进入二手交易治理与运营中心</p>
          </div>

          <!-- 错误提示区域 -->
          <div v-if="errorMessage" class="mb-6 p-3 bg-red-50/50 border border-red-100 rounded-lg text-[13px] text-red-600 flex items-start">
            <ShieldCheck class="w-4 h-4 mr-2 mt-0.5 shrink-0" />
            <span>{{ errorMessage }}</span>
          </div>

          <!-- 登录表单 -->
          <form @submit.prevent="handleLogin" class="space-y-5">
            <div>
              <label class="block text-[13px] font-medium text-gray-700 mb-1.5">账号</label>
              <input 
                v-model="loginForm.loginId" 
                type="text" 
                class="input-standard w-full" 
                placeholder="手机号或邮箱"
                :disabled="loading"
                autofocus
              />
            </div>

            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="block text-[13px] font-medium text-gray-700">密码</label>
                <a href="#" @click.prevent="showForgotModal = true" class="text-[12px] text-gray-500 hover:text-gray-900 transition-colors">忘记密码？</a>
              </div>
              <input 
                v-model="loginForm.password" 
                type="password" 
                class="input-standard w-full" 
                placeholder="请输入密码"
                :disabled="loading"
              />
            </div>

            <div class="flex items-center pt-1">
              <label class="flex items-center cursor-pointer group">
                <input 
                  v-model="loginForm.remember" 
                  type="checkbox" 
                  class="w-4 h-4 text-gray-900 border-gray-300 rounded focus:ring-gray-900/20 transition-colors"
                  :disabled="loading"
                />
                <span class="ml-2 text-[13px] text-gray-600 group-hover:text-gray-900 transition-colors">保持登录状态</span>
              </label>
            </div>

            <!-- 登录按钮 -->
            <button 
              type="submit" 
              class="btn-primary w-full py-2.5 mt-2"
              :disabled="loading"
            >
              <svg v-if="loading" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ loading ? '登录中...' : '登 录' }}
            </button>
          </form>

          <!-- 联调提示 -->
          <div class="mt-12 pt-6 border-t border-gray-100">
            <div class="bg-gray-50/50 rounded-lg p-4 text-[12px] text-gray-500 border border-gray-100">
              <p class="font-medium text-gray-700 mb-1.5">本地联调提示</p>
              <p class="mb-1">请使用管理员手机号或邮箱登录。</p>
              <p class="font-mono text-gray-600 bg-white px-2 py-1 rounded border border-gray-200 inline-block">13900000000 / admin123</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 忘记密码说明弹窗 -->
    <div v-if="showForgotModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-gray-900/40 backdrop-blur-sm transition-opacity" @click="showForgotModal = false">
      <div class="bg-white rounded-xl shadow-xl w-full max-w-sm overflow-hidden border border-gray-200/80" @click.stop>
        <div class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-[16px] font-bold text-gray-900">密码找回暂未开放</h3>
            <button @click="showForgotModal = false" class="text-gray-400 hover:text-gray-600 transition-colors">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>
            </button>
          </div>
          <div class="text-[13px] text-gray-600 space-y-3 leading-relaxed">
            <p>当前系统暂未开放自助找回密码功能。</p>
            <p>如需重置密码或处理账号异常，请联系平台管理员或通过内部 OA 流程发起申请。</p>
          </div>
        </div>
        <div class="px-6 py-4 bg-gray-50/80 border-t border-gray-100 flex justify-end">
          <button @click="showForgotModal = false" class="px-4 py-2 bg-gray-900 text-white text-[13px] font-medium rounded-lg hover:bg-gray-800 transition-colors shadow-sm">
            我知道了
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
