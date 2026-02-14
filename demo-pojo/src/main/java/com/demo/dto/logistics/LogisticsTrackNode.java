package com.demo.dto.logistics;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点
 *
 * 设计目的：
 * 1) 屏蔽不同 provider 的响应差异（字段命名、时间格式、状态文案）
 * 2) 作为 Service 层内部通用模型，便于统一转换为前端 VO
 */
@Data
public class LogisticsTrackNode {

    /**
     * 节点时间（系统内统一使用 LocalDateTime）
     */
    private LocalDateTime time;

    /**
     * 节点发生地点（如“上海转运中心”）
     */
    private String location;

    /**
     * 节点状态文案（如“已揽件 / 运输中 / 派送中 / 已签收”）
     */
    private String status;
}
