package com.demo.service;

/**
 * 通用系统站内信服务（与订单会话消息解耦）。
 *
 * 使用场景：
 * - 商品审核结果通知
 * - 商品强制下架通知
 * - 举报处理结果通知
 */
public interface SystemNoticeService {

    /**
     * 发送系统站内信（幂等）。
     *
     * @param toUserId    接收人用户 ID
     * @param clientMsgId 消息幂等键（建议基于 eventId 构造）
     * @param content     通知内容
     */
    void sendNotice(Long toUserId, String clientMsgId, String content);
}
