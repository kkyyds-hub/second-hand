package com.demo.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户违规记录 DTO（管理端列表项）。
 */
@Data
public class UserViolationDTO {
    /** 违规记录 ID。 */
    private Long id;
    /** 用户 ID。 */
    private Long userId;
    /** 违规类型。 */
    private String violationType;
    /** 违规描述。 */
    private String description;
    /** 证据内容（原始字段）。 */
    private String evidence;
    /** 处罚结果。 */
    private String punish;
    /** 信用分变动值。 */
    private Integer credit;
    /** 违规记录时间。 */
    private LocalDateTime recordTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
}
