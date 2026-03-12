import type { LoginParams, LoginResult } from '@/api/auth'
import { mockDelay } from './config'

/**
 * 管理端登录 mock：
 * - 后端没起时，保证登录流程可跑通；
 * - 维持与真实接口一致的返回结构。
 */
export async function mockLogin(data: LoginParams): Promise<LoginResult> {
  await mockDelay()

  const loginId = data.loginId?.trim()
  const password = data.password?.trim()

  if (!loginId || !password) {
    throw new Error('请输入账号和密码')
  }

  if (password.length < 3) {
    throw new Error('密码长度不合法')
  }

  return {
    token: `mock_admin_token_${Date.now()}`,
    user: {
      id: 1,
      username: loginId,
      nickname: '管理员',
    },
  }
}
