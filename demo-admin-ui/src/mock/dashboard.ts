import type { DashboardData } from '@/api/dashboard'
import { cloneData, mockDelay } from './config'

/**
 * 仪表盘 mock 数据种子。
 * 按首页四个视觉区块组织，review 时可以快速对照“页面模块 -> 数据来源”。
 */
const dashboardSeed: DashboardData = {
  // 顶部核心指标卡片。
  coreMetrics: [
    { title: '今日成交额 (GMV)', value: '￥ 542.45万', trend: '+15.2%', isUp: true, subtext: '较昨日同时段增加 15.2%' },
    { title: '新增付款订单', value: '4,105', trend: '+8.4%', isUp: true, subtext: '客单价稳定在 ￥ 1,321' },
    { title: '待审异常商品', value: '182', trend: '+12.1%', isUp: false, subtext: '超时未审积压: 14款' },
    { title: '售后争议 & 举报', value: '47', trend: '-5.0%', isUp: true, subtext: '平台强介入违规单: 21单' },
  ],
  // 中部审核工作台列表。
  reviewQueue: [
    { id: '审核-8902', item: 'Apple iPhone 15 Pro Max 256GB 钛金属', user: '数码回收_老王', type: '高价值', price: '￥ 6,950', time: '10分钟前', risk: '正常' },
    { id: '审核-8903', item: '全新未拆封 劳力士绿水鬼', user: 'WatchMaster', type: '品牌防伪', price: '￥ 125,000', time: '15分钟前', risk: '高风险' },
    { id: '审核-8904', item: 'Nike Air Force 1 联名款 42码', user: 'Sneaker搬砖人', type: '图片异常', price: '￥ 8,500', time: '22分钟前', risk: '中风险' },
    { id: '审核-8905', item: 'Sony A7M4 微单套机 95新', user: '光影流年', type: '常规审核', price: '￥ 13,200', time: '1小时前', risk: '正常' },
  ],
  // 待平台介入的纠纷/举报摘要。
  disputeQueue: [
    { id: '纠纷-102', reason: '商品描述严重不符 (瑕疵未告知)', target: '已签收', user: '买家投诉卖家', level: '紧急' },
    { id: '纠纷-103', reason: '疑似售假 / 鉴定未通过', target: '交易阻断', user: '查验中心拦截', level: '紧急' },
    { id: '纠纷-104', reason: '发货超时 / 虚假发货', target: '退款申请中', user: '买家发起', level: '中风险' },
  ],
  // 右侧风控预警摘要。
  riskAlerts: [
    { id: '风控-1', type: '同设备多账号批量发布', target: '设备指纹: FA98...21C', count: '14 个账号关联' },
    { id: '风控-2', type: '价格严重偏离市场均价', target: 'M2芯片 MacBook Air 挂牌 800元', count: '拦截违规展现' },
    { id: '风控-3', type: '支付异常预警', target: '同一黑卡高频拉起支付', count: '风控组已介入' },
  ],
}

export async function mockFetchDashboardData(): Promise<DashboardData> {
  await mockDelay()
  // 返回副本而不是原始种子，避免页面误改后污染下一次 mock 响应。
  return cloneData(dashboardSeed)
}
