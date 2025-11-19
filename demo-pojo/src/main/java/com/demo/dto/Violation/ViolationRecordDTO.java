package com.demo.dto.Violation;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
public class ViolationRecordDTO {

    private List<ViolationRecord> list;

    @Data
    @Builder
    public static class ViolationRecord {
        private Long id;               // 违规记录ID
        private Long userId;           // 用户ID
        private String username;       // 用户名
        private String violationType;  // 违规类型
        private String violationTypeDesc; // 违规类型描述
        private String orderId;        // 订单ID
        private String description;    // 违规描述
        private List<String> evidenceUrls; // 证据链接
        private String punishmentResult;  // 处罚结果
        private String recordTime;     // 记录时间
        private Integer creditScoreChange; // 信用分变化
    }
}
