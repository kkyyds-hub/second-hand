package com.demo.dto.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step3 - 消息展示 DTO
 */
@Data
public class MessageDTO {

    /** 主键 ID。 */
    private String id;
    /** 订单 ID。 */
    private Long orderId;
    /** 发送方用户 ID。 */
    private Long fromUserId;
    /** 接收方用户 ID。 */
    private Long toUserId;
    /** 字段：content。 */
    private String content;
    /** 字段：read。 */
    private Boolean read;
    /** 创建时间。 */
    private LocalDateTime createTime;
}
