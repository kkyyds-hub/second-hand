package com.demo.dto.logistics;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 物流查询结果（Provider -> Service 的标准返回对象）
 *
 * 约束：
 * 1) provider 必填，用于标识当前数据来源（mock / delivery-tracker）
 * 2) trace 允许为空，但不建议返回 null（降低前端判空复杂度）
 * 3) 查询失败时应优先返回“空轨迹 + lastSyncTime”，而不是抛异常中断主流程
 */
@Data
public class LogisticsTrackResult {

    /**
     * 轨迹来源提供方标识
     */
    private String provider;

    /**
     * 最近一次同步时间（用于前端展示“数据更新时间”）
     */
    private LocalDateTime lastSyncTime;

    /**
     * 轨迹节点列表（按时间升序或业务约定顺序返回）
     */
    private List<LogisticsTrackNode> trace = new ArrayList<>();
}
