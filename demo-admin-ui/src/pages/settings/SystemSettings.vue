<script setup lang="ts">
import { Settings, ShieldAlert, Bell, Users, Construction } from 'lucide-vue-next'

/**
 * 设置模块配置数据
 * 用于渲染页面上的设置卡片占位
 */
const settingModules = [
  {
    id: 'account',
    title: '账号与权限',
    description: '管理后台运营人员账号、角色分配及操作权限控制。',
    icon: Users,
    colorClass: 'text-blue-600 bg-blue-50'
  },
  {
    id: 'notification',
    title: '通知与消息',
    description: '配置系统告警接收人、站内信模板及短信/邮件发送策略。',
    icon: Bell,
    colorClass: 'text-orange-600 bg-orange-50'
  },
  {
    id: 'risk',
    title: '风控策略',
    description: '调整交易拦截阈值、敏感词词库及自动封禁规则。',
    icon: ShieldAlert,
    colorClass: 'text-red-600 bg-red-50'
  }
]
</script>

<template>
  <div class="space-y-6 max-w-[1600px] mx-auto">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-center pb-2">
      <div>
        <h1 class="text-xl font-bold text-gray-900">系统设置</h1>
        <p class="text-sm text-gray-500 mt-1">管理平台全局配置、运营权限及底层风控规则</p>
      </div>
    </div>

    <!-- 建设中提示横幅 -->
    <div class="bg-blue-50 border border-blue-200 rounded-sm p-4 flex items-start sm:items-center gap-3 shadow-sm">
      <Construction class="w-5 h-5 text-blue-600 shrink-0 mt-0.5 sm:mt-0" />
      <div>
        <h3 class="text-sm font-bold text-blue-900">功能建设中</h3>
        <p class="text-xs text-blue-700 mt-1">
          系统设置模块正在进行底层架构升级，当前仅展示模块规划，具体配置功能将在后续版本中陆续开放。
        </p>
      </div>
    </div>

    <!-- 设置模块卡片网格 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <div 
        v-for="mod in settingModules" 
        :key="mod.id"
        class="card p-6 flex flex-col hover:border-blue-200 transition-colors cursor-not-allowed opacity-80"
      >
        <div class="flex items-center gap-3 mb-4">
          <div class="p-2.5 rounded-md" :class="mod.colorClass">
            <component :is="mod.icon" class="w-5 h-5" />
          </div>
          <h2 class="text-base font-bold text-gray-800">{{ mod.title }}</h2>
        </div>
        
        <p class="text-sm text-gray-500 leading-relaxed flex-1">
          {{ mod.description }}
        </p>
        
        <div class="mt-6 pt-4 border-t border-gray-100 flex justify-between items-center">
          <span class="text-xs text-gray-400 flex items-center gap-1">
            <Settings class="w-3 h-3" /> 暂未开放
          </span>
          <button class="text-sm text-gray-400 font-medium cursor-not-allowed" disabled>
            去配置
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
