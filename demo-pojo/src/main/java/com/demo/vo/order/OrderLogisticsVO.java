package com.demo.vo.order;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单物流视图对象（直接返回给前端）
 *
 * 语义说明：
 * - status：订单主状态（pending/paid/shipped/completed/cancelled）
 * - shipping*：订单表中的物流快照字段
 * - trace：动态轨迹，来自 provider 查询结果
 */
@Data
public class OrderLogisticsVO {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单状态（数据库状态值）
     */
    private String status;

    /**
     * 物流公司（快照字段）
     */
    private String shippingCompany;

    /**
     * 运单号（快照字段）
     */
    private String trackingNo;

    /**
     * 发货时间（快照字段）
     */
    private LocalDateTime shipTime;

    /**
     * 当前使用的 provider 名称
     */
    private String provider;

    /**
     * 最近一次轨迹同步时间
     */
    private LocalDateTime lastSyncTime;

    /**
     * 轨迹节点（未发货时通常为空列表）
     */
    private List<LogisticsTraceItemVO> trace = new ArrayList<>();
}
