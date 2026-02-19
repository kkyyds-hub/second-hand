package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 用户列表查询参数。
 */
@Data
public class UserQueryDTO extends PageQueryDTO {

    /** 关键字（用户名/手机号）。 */
    private String keyword;

    /** 注册时间起点。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 注册时间终点。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 地区筛选。 */
    private String region;
    /** 最小信用分。 */
    private Integer minCreditScore;
    /** 最大信用分。 */
    private Integer maxCreditScore;
    /** 用户状态。 */
    private String status;
}
