package com.demo.vo.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端纠纷与违规页聚合返回对象。
 * 让前端一次请求即可拿到指标卡和工单列表，避免自己拼多个接口。
 */
@Data
public class AdminAuditOverviewVO {

    /** 页面顶部统计卡。 */
    private AuditStats stats = new AuditStats();

    /** 当前筛选条件下的工单列表。 */
    private List<AuditTicketItem> tickets = new ArrayList<>();

    @Data
    public static class AuditStats {
        /** 待处理纠纷数量。 */
        private Long pendingDisputes = 0L;
        /** 紧急举报数量。 */
        private Long urgentReports = 0L;
        /** 平台强介入工单数量。 */
        private Long platformIntervention = 0L;
        /** 今日新增线索数量。 */
        private Long todayNewClues = 0L;
    }

    @Data
    public static class AuditTicketItem {
        /** 页面展示用工单编号。 */
        private String id;
        /** 工单类型（DISPUTE / REPORT / RISK）。 */
        private String type;
        /** 标题或原因。 */
        private String title;
        /** 关联对象，如订单号、商品号、用户号。 */
        private String target;
        /** 风险等级。 */
        private String riskLevel;
        /** 页面状态。 */
        private String status;
        /** 创建时间。 */
        private LocalDateTime createTime;
        /** 详情描述。 */
        private String description;
        /** 原始业务主键，便于后续接真实处理动作。 */
        private Long sourceId;
        /** 原始业务状态，便于后续做更精细的操作判断。 */
        private String sourceStatus;
    }
}
