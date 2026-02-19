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
    /** 字段：page。 */
    private Integer page = 1;

    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    /** 字段：pageSize。 */
    private Integer pageSize = 10;

    /** 状态。 */
    private String status;
    /** 时间字段。 */
    private LocalDateTime startTime;
    /** 时间字段。 */
    private LocalDateTime endTime;

    /** 字段：sortField。 */
    private String sortField = "createTime";
    /** 字段：sortOrder。 */
    private String sortOrder = "desc";
}
