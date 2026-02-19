package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 用户违规记录实体。
 */
@Data
public class UserViolation {
    /** 违规记录 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 业务关联 ID（如订单 ID）。 */
    private Long bizId;
    /** 违规类型。 */
    private String violationType;
    /** 违规描述。 */
    private String description;
    /** 证据附件。 */
    private String evidence;
    /** 处罚结果。 */
    private String punish;
    /** 信用分变动值。 */
    private Integer credit;

    /** 记录时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordTime;
    /** 创建时间。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
