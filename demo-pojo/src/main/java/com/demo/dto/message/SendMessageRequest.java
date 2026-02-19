package com.demo.dto.message;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Day13 Step3 - 发送消息请求
 */
@Data
public class SendMessageRequest {

    /**
     * 接收方用户 ID
     */
    @NotNull(message = "接收方用户 ID 不能为空")
    private Long toUserId;

    /**
     * 客户端生成的幂等键（UUID/雪花 ID）
     */
    @NotBlank(message = "客户端消息ID不能为空")
    private String clientMsgId;

    /**
     * 消息内容（Day13 仅支持文本）
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(min = 1, max = 500, message = "消息内容长度需在 1~500 字符")
    private String content;
}

