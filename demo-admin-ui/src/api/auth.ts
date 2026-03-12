import request from '@/utils/request'

/**
 * 登录请求参数
 */
export interface LoginParams {
  loginId: string
  password: string
}

/**
 * 登录成功返回的数据结构
 */
export interface LoginResult {
  token: string
  user: {
    id: number
    username: string
    nickname: string
  }
}

/**
 * 管理端登录接口
 * @param data 包含账号和密码的登录参数
 * @returns 返回包含 token 和用户信息的 Promise
 */
export function login(data: LoginParams) {
  // 注意：这里的泛型 LoginResult 会被 request.ts 里的拦截器拆包，
  // 最终业务代码拿到的直接是 data 里的内容，而不是 { code, msg, data }
  return request.post<any, LoginResult>('/admin/employee/login', data)
}
