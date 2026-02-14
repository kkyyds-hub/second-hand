package com.demo.logistics;

import com.demo.dto.logistics.LogisticsTrackResult;

import java.time.LocalDateTime;

/**
 * 物流查询 Provider 抽象。
 *
 * 设计目标：
 * 1) 解耦业务服务与具体第三方 API
 * 2) 支持 mock 与真实 provider 平滑切换
 * 3) 统一返回结构，避免 Controller/Service 关心三方字段细节
 */
public interface LogisticsProvider {

    /**
     * @return provider 名称（用于日志与响应标识）
     */
    String getName();

    /**
     * 查询轨迹。
     *
     * 契约：
     * - 查询失败时应尽量返回“空轨迹结果”，不抛出致命异常阻断主流程
     * - trackingNo 为空时可直接返回空结果
     *
     * @param shippingCompany 物流公司
     * @param trackingNo 运单号
     * @param shipTime 发货时间（供 mock 或三方查询构造策略使用）
     * @return 标准化轨迹结果
     */
    LogisticsTrackResult query(String shippingCompany, String trackingNo, LocalDateTime shipTime);
}
