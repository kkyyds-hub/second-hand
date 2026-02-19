package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品违规记录实体。
 */
@Data
public class ProductViolation {
    /** 违规记录 ID。 */
    private Long id;
    /** 商品 ID。 */
    private Long productId;
    /** 违规类型。 */
    private String violationType;
    /** 违规描述。 */
    private String description;
    /** 违规证据 URL（多条可序列化存储）。 */
    private String evidenceUrls;
    /** 处罚结果。 */
    private String punishmentResult;
    /** 信用分变动值。 */
    private Integer creditScoreChange;
    /** 记录状态（如 active/inactive）。 */
    private String status;
    /** 记录时间。 */
    private LocalDateTime recordTime;
}
