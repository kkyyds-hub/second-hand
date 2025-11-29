package com.demo.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String mobile;
    private String avatar;
    private String nickname;
    private String bio;
    private String email;
    private LocalDateTime registerTime;
    private String lastLoginIp;
    private Integer creditScore;
    private Integer productCount;
    private String status;
    private String region;
}