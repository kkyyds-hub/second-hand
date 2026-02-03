package com.demo.dto.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step3 - 消息展示 DTO
 */
@Data
public class MessageDTO {

    private String id;
    private Long orderId;
    private Long fromUserId;
    private Long toUserId;
    private String content;
    private Boolean read;
    private LocalDateTime createTime;
}
