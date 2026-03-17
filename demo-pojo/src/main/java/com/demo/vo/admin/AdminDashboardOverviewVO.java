package com.demo.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台首页总览聚合返回对象。
 *
 * 这个对象不是数据库实体，而是专门给“工作台首页”准备的页面级返回模型。
 * 目的是让前端一次请求就能拿到首页所需的主要数据块，减少自己在前端拼装多个接口。
 */
@Data
public class AdminDashboardOverviewVO {

    /**
     * 顶部指标卡。
     */
    private List<MetricItem> coreMetrics = new ArrayList<>();

    /**
     * 待审核商品队列。
     */
    private List<AdminDashboardReviewQueueItemVO> reviewQueue = new ArrayList<>();

    /**
     * 平台介入纠纷队列。
     */
    private List<DisputeQueueItem> disputeQueue = new ArrayList<>();

    /**
     * 风控预警列表。
     */
    private List<RiskAlertItem> riskAlerts = new ArrayList<>();

    @Data
    public static class MetricItem {
        private String title;
        private String value;
        private String trend;
        private Boolean isUp;
        private String subtext;
    }

    @Data
    public static class DisputeQueueItem {
        private String id;
        private String reason;
        private String target;
        private String user;
        private String level;
    }

    @Data
    public static class RiskAlertItem {
        private String id;
        private String type;
        private String target;
        private String count;
    }
}
