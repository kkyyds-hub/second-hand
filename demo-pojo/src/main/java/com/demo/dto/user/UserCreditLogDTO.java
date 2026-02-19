package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信用分变更流水 DTO
 */
@Data
public class UserCreditLogDTO {
    /** 主键 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 字段：delta。 */
    private Integer delta;
    private String reasonType;  // 变更原因类型 dbValue
    /** 关联业务 ID。 */
    private Long refId;
    /** 字段：scoreBefore。 */
    private Integer scoreBefore;
    /** 字段：scoreAfter。 */
    private Integer scoreAfter;
    /** 字段：reasonNote。 */
    private String reasonNote;
    /** 创建时间。 */
    private LocalDateTime createTime;
}

