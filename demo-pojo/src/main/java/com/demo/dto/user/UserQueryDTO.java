package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 用户列表查询参数。
 * 管理端会将该对象直接映射为查询条件，传入分页查询接口。
 */
@Data
public class UserQueryDTO extends PageQueryDTO {

    /** 关键字（支持匹配用户名、手机号、邮箱）。 */
    private String keyword;

    /** 注册时间起点。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 注册时间终点。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 地区筛选（预留字段，便于后续扩展地域化运营筛选）。 */
    private String region;
    /** 最小信用分。 */
    private Integer minCreditScore;
    /** 最大信用分。 */
    private Integer maxCreditScore;
    /** 用户状态（如 active / inactive / frozen / banned）。 */
    private String status;
    /** 角色筛选（如 BUYER_NORMAL / SELLER_ENTERPRISE 等）。 */
    private String role;
}
