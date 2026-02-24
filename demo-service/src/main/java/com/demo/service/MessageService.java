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

    /**
     * Day16 Step6 - 查询系统通知列表（分页）。
     * 说明：
     * 1) 系统通知固定写入 Mongo 的 orderId=0 槽位；
     * 2) 权限按收件人 toUserId 隔离，不走订单归属校验。
     */
    PageResult<MessageDTO> listSystemNotices(Long currentUserId, Integer page, Integer pageSize);

    /**
     * Day16 Step6 - 查询系统通知详情。
     * 仅允许读取“发给当前用户”的系统通知文档。
     */
    MessageDTO getSystemNoticeDetail(String messageId, Long currentUserId);

    /**
     * Day16 Step6 - 一键已读系统通知。
     * 将当前用户在系统通知槽位(orderId=0)下的未读消息批量置为已读。
     */
    String markSystemNoticesAsRead(Long currentUserId);
}
