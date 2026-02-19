package com.demo.dto.Violation;

import lombok.Data;

import java.util.List;

/**
 * 商品违规信息 DTO。
 */
@Data
public class ProductViolationDTO {
    /** 商品 ID。 */
    private Long productId;
    /** 违规类型。 */
    private String violationType;
    /** 违规描述。 */
    private String description;
    /** 证据链接列表。 */
    private List<String> evidenceUrls;
    /** 处罚结果。 */
    private String punishmentResult;
    /** 信用分变动值。 */
    private Integer creditScoreChange;
}
