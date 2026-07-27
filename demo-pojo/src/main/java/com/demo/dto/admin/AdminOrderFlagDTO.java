package com.demo.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端只读订单标记历史。
 */
@Data
public class AdminOrderFlagDTO {
    private Long id;
    private String type;
    private String remark;
    private Long createdBy;
    private String createdByNickname;
    private LocalDateTime createTime;
}
