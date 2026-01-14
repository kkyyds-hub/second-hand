package com.demo.dto.admin;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserViolationDTO {
    private Long id;
    private Long userId;
    private String violationType;
    private String description;
    private String evidence;
    private String punish;
    private Integer credit;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
