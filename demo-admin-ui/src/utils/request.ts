import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'

/**
 * 这是后端统一返回结果的类型声明。
 *
 * 你的 Spring Boot 后端不是直接返回业务对象，
 * 而是统一返回：
 * {
 *   code: 1,
 *   msg: "success",
 *   data: 真正的数据
 * }
 *
 * 所以前端需要先认识这层“外壳”，后面才能把 data 拆出来给页面用。
 */
export interface ApiResult<T> {
  // code 是业务状态码，不是 HTTP 状态码。
  code: number
  // msg 是后端返回的提示信息，例如 success 或错误描述。
  msg: string
  // data 才是页面真正想要使用的业务数据。
  data: T
}

/**
 * 这是我们现在约定的“管理端 token”本地存储 key。
 *
 * 为什么单独起一个名字：
 * 因为管理端和用户端未来可能都要存 token，
 * 用更明确的 key 名更不容易混淆。
 */
const ADMIN_TOKEN_KEY = 'admin_token'

/**
 * 这是为了兼容旧代码保留的 token key 列表。
 *
 * Gemini 之前生成过 `jwt_token` 和 `token` 这种旧命名，
 * 这里保留兼容，可以减少你联调时“明明有 token 却读不到”的问题。
 */
const LEGACY_TOKEN_KEYS = ['jwt_token', 'token']

/**
 * 读取本地 token。
 *
 * 读取顺序是：
 * 1. 先读新 key：admin_token
 * 2. 再尝试读旧 key：jwt_token / token
 *
 * 这样做的目的是让联调过渡更平滑。
 */
function readAdminToken() {
  // 先尝试读取当前规范使用的 key。
  const direct = localStorage.getItem(ADMIN_TOKEN_KEY)

  // 如果新 key 已经有值，直接返回。
  if (direct) {
    return direct
  }

  // 如果新 key 没有值，就回退读取旧 key。
  for (const key of LEGACY_TOKEN_KEYS) {
    // 逐个尝试读取旧 key。
    const value = localStorage.getItem(key)

    // 只要某个旧 key 有值，就立刻返回。
    if (value) {
      return value
    }
  }

  // 如果都没读到，返回空字符串。
  return ''
}

/**
 * 保存管理端 token。
 *
 * 登录成功后通常会调用这个方法。
 * 这里除了存 `admin_token`，也顺手存一份 `token`，
 * 是为了兼容当前项目里可能还依赖旧命名的逻辑。
 */
export function saveAdminToken(token: string) {
  // 保存到我们现在推荐的新 key。
  localStorage.setItem(ADMIN_TOKEN_KEY, token)
  // 同时兼容一份旧 key，降低联调期间的断层风险。
  localStorage.setItem('token', token)
}

/**
 * 清空管理端 token。
 *
 * 常见使用场景：
 * 1. 用户主动退出登录
 * 2. 后端返回 401，说明 token 已失效
 * 3. 联调时手动清理旧状态
 */
export function clearAdminToken() {
  // 删除当前规范 key。
  localStorage.removeItem(ADMIN_TOKEN_KEY)
  // 删除 Gemini 早期生成代码里可能留下的 key。
  localStorage.removeItem('jwt_token')
  // 删除兼容使用的 key。
  localStorage.removeItem('token')
}

/**
 * 创建全局 Axios 实例。
 *
 * 为什么要统一创建：
 * 因为以后所有页面都应该走这一个请求实例，
 * 这样 token、超时、错误处理、返回值拆包都能统一管理。
 */
const service: AxiosInstance = axios.create({
  // baseURL 来自环境变量：
  // 开发环境一般是 /api，配合 Vite 代理转发到本地后端。
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  // 请求 10 秒超时，避免接口一直卡死。
  timeout: 10000,
  // 统一声明 JSON 请求头，适合当前大部分接口。
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * 请求拦截器。
 *
 * 每一次请求真正发出之前，都会先经过这里。
 * 这里最重要的事情，就是自动把 token 塞进请求头。
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 先从本地读取 token。
    const token = readAdminToken()

    // 只有当 token 存在并且 headers 可写时，才往请求头里放。
    if (token && config.headers) {
      /**
       * 这里是这套项目最容易搞错的点：
       *
       * 你的后端管理端拦截器读取的是自定义请求头 `token`，
       * 不是很多教程里常见的：
       * Authorization: Bearer xxx
       *
       * 所以这里必须严格按后端协议来写。
       */
      config.headers.token = token
    }

    // 处理完后把配置对象继续往下传。
    return config
  },
  (error: unknown) => {
    // 如果请求在发出前就出错了，这里统一打印日志。
    console.error('Request error:', error)
    // 把错误继续抛出去，交给调用方处理。
    return Promise.reject(error)
  },
)

/**
 * 响应拦截器。
 *
 * 后端响应回来后，会先经过这里。
 * 这里要做的事是：
 * 1. 判断是不是你后端统一的 Result<T> 结构
 * 2. 如果是，就检查业务 code
 * 3. 成功时直接把 data 拆给页面
 * 4. 失败时统一抛错
 */
service.interceptors.response.use(
  <T>(response: AxiosResponse<ApiResult<T> | T>) => {
    // Axios 原始返回对象里，真正的响应体在 response.data。
    const res = response.data

    // 如果响应体是对象，并且同时有 code / msg 字段，
    // 就认为它是后端统一 Result<T> 结构。
    if (res && typeof res === 'object' && 'code' in res && 'msg' in res) {
      // 把它收窄成我们前面声明的 ApiResult<T>。
      const apiResult = res as ApiResult<T>

      // 你的后端成功码是 1，不是常见的 200。
      if (apiResult.code !== 1) {
        // 如果业务 code 不为 1，就按失败处理。
        return Promise.reject(new Error(apiResult.msg || '请求失败'))
      }

      // 如果业务 code 正常，直接把真正的数据 data 返回给页面。
      return apiResult.data
    }

    // 如果后端返回的本来就不是 Result<T> 包装结构，
    // 那就原样返回，兼容文件导出或其他特殊接口。
    return res
  },
  (error: any) => {
    // 读取 HTTP 状态码，例如 401 / 403 / 500。
    const status = error.response?.status
    // 优先读取后端返回的业务错误信息，没有再回退到 Axios 自己的错误信息。
    const message = error.response?.data?.msg || error.response?.data?.message || error.message

    // 按常见状态码分类处理，方便联调时快速定位问题。
    switch (status) {
      case 401:
        /**
         * 401 一般表示：
         * - 没带 token
         * - token 过期
         * - token 非法
         *
         * 这里先清掉本地 token，避免后续请求继续带着失效 token。
         */
        console.error('Unauthorized (401): admin token missing or expired.')
        clearAdminToken()
        break
      case 403:
        // 403 表示已登录，但当前账号没有权限。
        console.error('Forbidden (403): no permission for this action.')
        break
      case 404:
        // 404 说明接口路径不对，是联调时很常见的问题。
        console.error(`Not Found (404): ${error.config?.url || 'unknown url'}`)
        break
      case 500:
        // 500 说明请求到达后端了，但后端内部执行异常。
        console.error('Server Error (500): internal backend error.')
        break
      default:
        // 其他错误统一打印，避免静默失败。
        console.error(`HTTP Error (${status || 'Unknown'}): ${message}`)
    }

    // 错误继续抛出去，让页面层决定是否兜底显示 mock 数据。
    return Promise.reject(error)
  },
)

// 对外导出这个统一请求实例，页面和 API 文件都从这里走。
export default service
