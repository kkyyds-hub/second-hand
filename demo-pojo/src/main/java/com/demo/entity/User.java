package com.demo.entity;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String mobile;
    private String nickname;
    private String bio;
    private String email;
    private String avatar;
    private Integer creditScore = 100;  // 初始信用分100
    private String status = "active";
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")// active-正常, banned-封禁
    private LocalDateTime createTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}