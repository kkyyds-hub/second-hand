package com.demo.dto.Violation;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户违规记录分页响应 DTO。
 */
@Data
public class ViolationRecordDTO {

    /** 违规记录列表。 */
    private List<ViolationRecord> list;

    /**
     * 违规记录项。
     */
    @Data
    @Builder
    public static class ViolationRecord {
        /** 违规记录 ID。 */
        private Long id;
        /** 用户 ID。 */
        private Long userId;
        /** 用户名。 */
        private String username;
        /** 违规类型编码。 */
        private String violationType;
        /** 违规类型描述。 */
        private String violationTypeDesc;
        /** 关联订单 ID。 */
        private String orderId;
        /** 违规描述。 */
        private String description;
        /** 证据链接列表。 */
        private List<String> evidenceUrls;
        /** 处罚结果。 */
        private String punishmentResult;
        /** 记录时间。 */
        private LocalDateTime recordTime;
        /** 信用分变动值。 */
        private Integer creditScoreChange;
    }
}
