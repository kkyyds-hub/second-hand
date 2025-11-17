package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class UserQueryDTO extends PageQueryDTO {

    private String keyword;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")// 关键字搜索（用户名、手机号）
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")// 注册时间-开始
    private LocalDateTime endTime;        // 注册时间-结束
    private String region;               // 地区筛选
    private Integer minCreditScore;      // 最小信用分
    private Integer maxCreditScore;      // 最大信用分
    private String status;               // 用户状态
}