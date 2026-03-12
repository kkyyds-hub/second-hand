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
  <div class="min-h-screen bg-slate-900 flex items-center justify-center p-4 sm:p-8 font-sans selection:bg-blue-200">
    <!-- 登录容器：最大宽度限制，大屏左右双栏，小屏上下堆叠 -->
    <div class="max-w-5xl w-full flex flex-col lg:flex-row bg-slate-800 rounded-xl shadow-2xl overflow-hidden border border-slate-700">
      
      <!-- 左侧：品牌与说明区 (深色背景) -->
      <div class="lg:w-5/12 p-8 lg:p-12 flex flex-col justify-between relative overflow-hidden bg-slate-900">
        <!-- 背景装饰光晕 -->
        <div class="absolute top-0 left-0 w-full h-full overflow-hidden z-0 opacity-20 pointer-events-none">
          <div class="absolute -top-24 -left-24 w-96 h-96 rounded-full bg-blue-600 blur-3xl"></div>
          <div class="absolute bottom-0 right-0 w-64 h-64 rounded-full bg-indigo-600 blur-3xl"></div>
        </div>

        <div class="relative z-10">
          <!-- Logo 与系统名称 -->
          <div class="flex items-center space-x-3 mb-12">
            <div class="flex items-center justify-center w-10 h-10 bg-blue-600 rounded shadow-lg">
              <span class="text-white text-lg font-black tracking-tighter">KK</span>
            </div>
            <span class="text-2xl font-bold text-white tracking-widest">运营后台</span>
          </div>

          <!-- 核心标语 -->
          <h1 class="text-3xl font-bold text-white mb-6 leading-tight">
            专业、高效的<br/>
            <span class="text-blue-400">业务管理中枢</span>
          </h1>
          
          <p class="text-slate-400 mb-10 leading-relaxed">
            为平台运营人员提供全链路的数据监控、用户管理、商品审核与风控仲裁能力，保障平台生态健康运转。
          </p>

          <!-- 系统能力说明 -->
          <div class="space-y-6 hidden sm:block">
            <div class="flex items-start space-x-4">
              <div class="bg-slate-800 p-2 rounded text-blue-400 mt-1">
                <Activity class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-white font-medium mb-1">实时大盘监控</h3>
                <p class="text-sm text-slate-400">核心业务指标秒级更新，异常波动智能预警</p>
              </div>
            </div>
            <div class="flex items-start space-x-4">
              <div class="bg-slate-800 p-2 rounded text-blue-400 mt-1">
                <ShieldCheck class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-white font-medium mb-1">智能风控拦截</h3>
                <p class="text-sm text-slate-400">多维度安全策略，自动识别并阻断高危交易</p>
              </div>
            </div>
            <div class="flex items-start space-x-4">
              <div class="bg-slate-800 p-2 rounded text-blue-400 mt-1">
                <Users class="w-5 h-5" />
              </div>
              <div>
                <h3 class="text-white font-medium mb-1">全景用户画像</h3>
                <p class="text-sm text-slate-400">深度洞察用户行为，精准定位违规账号</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部版权与安全提示 -->
        <div class="relative z-10 mt-12 pt-6 border-t border-slate-800 text-xs text-slate-500 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
          <span>&copy; 2026 KK Platform. All rights reserved.</span>
          <span class="flex items-center"><Lock class="w-3 h-3 mr-1"/> 内部系统，严禁外传</span>
        </div>
      </div>

      <!-- 右侧：登录表单区 (白色卡片) -->
      <div class="lg:w-7/12 bg-white p-8 lg:p-16 flex flex-col justify-center relative">
        <div class="max-w-md w-full mx-auto">
          <!-- 登录标题 -->
          <div class="mb-10">
            <h2 class="text-2xl font-bold text-gray-900 mb-2">欢迎进入运营后台</h2>
            <p class="text-sm text-gray-500">登录后可查看平台总览、用户与商家、审核与风控数据</p>
          </div>

          <!-- 错误提示区域 -->
          <div v-if="errorMessage" class="mb-6 p-3 bg-red-50 border border-red-200 rounded text-sm text-red-600 flex items-start animate-in fade-in slide-in-from-top-2">
            <ShieldCheck class="w-4 h-4 mr-2 mt-0.5 shrink-0" />
            <span>{{ errorMessage }}</span>
          </div>

          <!-- 登录表单 -->
          <form @submit.prevent="handleLogin" class="space-y-6">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">账号</label>
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
              <label class="block text-sm font-medium text-gray-700 mb-1">密码</label>
              <input 
                v-model="loginForm.password" 
                type="password" 
                class="input-standard w-full" 
                placeholder="请输入密码"
                :disabled="loading"
              />
            </div>

            <div class="flex items-center justify-between">
              <label class="flex items-center cursor-pointer">
                <input 
                  v-model="loginForm.remember" 
                  type="checkbox" 
                  class="w-4 h-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                  :disabled="loading"
                />
                <span class="ml-2 text-sm text-gray-600">保持登录状态</span>
              </label>
              <a href="#" class="text-sm text-blue-600 hover:text-blue-800 font-medium transition-colors">忘记密码？</a>
            </div>

            <!-- 登录按钮 -->
            <button 
              type="submit" 
              class="btn-primary w-full py-2.5 text-base flex justify-center items-center"
              :disabled="loading"
            >
              <svg v-if="loading" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ loading ? '登录中...' : '登 录' }}
            </button>
          </form>

          <!-- 联调提示：这里给出当前本地环境可用的登录方式，减少测试时输错账号。 -->
          <div class="mt-10 pt-6 border-t border-gray-100">
            <div class="bg-gray-50 rounded p-4 text-xs text-gray-500 border border-gray-100">
              <p class="font-medium text-gray-700 mb-1">当前本地联调提示：</p>
              <p>请使用管理员手机号或邮箱登录，不支持直接输入用户名。</p>
              <p class="mt-1">示例账号：13900000000 / admin123</p>
              <p class="mt-1 text-gray-400">如本地库数据有变动，请以你当前数据库中的管理员账号为准。</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
