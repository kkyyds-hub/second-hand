package com.demo.service;

import com.demo.dto.message.MessageDTO;
import com.demo.dto.message.SendMessageRequest;
import com.demo.result.PageResult;

/**
 * Day13 Step3 - 站内消息服务
 */
public interface MessageService {

    /**
     * 发送消息（频控 + 幂等）
     */
    MessageDTO sendMessage(Long orderId, Long currentUserId, SendMessageRequest request);

    /**
     * 拉取订单会话消息（分页，按 createTime 升序）
     */
    PageResult<MessageDTO> listMessages(Long orderId, Long currentUserId, Integer page, Integer pageSize);

    /**
     * 获取未读数
     */
    Long getUnreadCount(Long currentUserId);

    /**
     * 标记订单会话已读
     */
    String markAsRead(Long orderId, Long currentUserId);
}
