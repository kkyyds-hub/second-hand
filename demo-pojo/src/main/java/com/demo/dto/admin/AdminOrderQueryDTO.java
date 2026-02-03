package com.demo.dto.admin;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDateTime;

/**
 * Day13 Step7 - 后台订单分页查询参数
 */
@Data
public class AdminOrderQueryDTO {

    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer pageSize = 10;

    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String sortField = "createTime";
    private String sortOrder = "desc";
}
