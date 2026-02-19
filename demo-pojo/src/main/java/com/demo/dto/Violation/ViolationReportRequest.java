package com.demo.dto.Violation;

import lombok.Data;

import java.util.List;

/**
 * 违规上报请求 DTO。
 */
@Data
public class ViolationReportRequest {

    /** 被举报用户 ID。 */
    private Long userId;
    /** 违规类型编码。 */
    private String violationType;
    /** 关联订单 ID。 */
    private String orderId;
    /** 违规描述。 */
    private String description;
    /** 证据链接列表。 */
    private List<String> evidenceUrls;
    /** 处罚结果。 */
    private String punishmentResult;
    /** 处罚时长（天）。 */
    private Integer punishDuration;
}
