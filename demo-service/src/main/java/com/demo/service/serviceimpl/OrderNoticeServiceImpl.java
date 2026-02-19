package com.demo.service.serviceimpl;

import com.demo.entity.Message;
import com.demo.entity.Order;
import com.demo.entity.OrderRefundTask;
import com.demo.repository.MessageRepository;
import com.demo.service.OrderNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单系统通知实现。
 *
 * 设计要点：
 * 1) 使用 MessageRepository 直接落库，避免受用户消息权限限制。
 * 2) 通过 clientMsgId 做幂等，重复发送不会产生重复消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNoticeServiceImpl implements OrderNoticeService {

    /**
     * 系统用户 ID（站内消息显示“系统通知”可由前端按此 ID识别）。
     */
    private static final Long SYSTEM_USER_ID = 0L;

    private final MessageRepository messageRepository;

    @Value("${order.notice.ship-timeout-cancel-enabled:true}")
    private boolean shipTimeoutCancelNoticeEnabled;

    @Value("${order.notice.refund-success-enabled:true}")
    private boolean refundSuccessNoticeEnabled;

    @Value("${order.notice.ship-reminder-enabled:true}")
    private boolean shipReminderNoticeEnabled;

    /**
     * 发送“超时未发货已取消”通知。
     */
    @Override
    public void notifyShipTimeoutCancelled(Order order) {
        if (!shipTimeoutCancelNoticeEnabled) {
            return;
        }
        if (order == null || order.getId() == null) {
            return;
        }
        String content = "订单因超时未发货已自动取消，系统已发起退款。订单 ID：" + order.getId();

        saveNotice(order.getId(), order.getBuyerId(), "SYS-SHIP-TIMEOUT-" + order.getId() + "-BUYER", content);
        saveNotice(order.getId(), order.getSellerId(), "SYS-SHIP-TIMEOUT-" + order.getId() + "-SELLER", content);
    }

    /**
     * 发送“退款成功”通知。
     */
    @Override
    public void notifyRefundSuccess(Order order, OrderRefundTask refundTask) {
        if (!refundSuccessNoticeEnabled) {
            return;
        }
        if (order == null || order.getId() == null || refundTask == null) {
            return;
        }
        String content = "订单退款处理成功。订单 ID：" + order.getId() + "，退款类型：" + refundTask.getRefundType();

        saveNotice(order.getId(), order.getBuyerId(), "SYS-REFUND-SUCCESS-" + order.getId() + "-BUYER", content);
        saveNotice(order.getId(), order.getSellerId(), "SYS-REFUND-SUCCESS-" + order.getId() + "-SELLER", content);
    }

    /**
     * 发送“即将超时未发货”提醒通知。
     */
    @Override
    public void notifyShipReminder(Order order, String level, String remaining, String clientMsgId) {
        if (!shipReminderNoticeEnabled) {
            return;
        }
        if (order == null || order.getId() == null || order.getSellerId() == null) {
            return;
        }
        String safeLevel = (level == null || level.trim().isEmpty()) ? "H1" : level.trim().toUpperCase();
        String safeRemain = (remaining == null || remaining.trim().isEmpty()) ? "即将到期" : remaining.trim();
        String orderNo = (order.getOrderNo() == null || order.getOrderNo().trim().isEmpty())
                ? String.valueOf(order.getId())
                : order.getOrderNo().trim();

        String content = "订单号 " + orderNo + " 距离自动取消还剩 " + safeRemain
                + "（提醒档位：" + safeLevel + "），请尽快完成发货。";

        String msgId = (clientMsgId == null || clientMsgId.trim().isEmpty())
                ? "SYS-SHIP-REMIND-" + order.getId() + "-" + safeLevel
                : clientMsgId.trim();

        saveNotice(order.getId(), order.getSellerId(), msgId, content);
    }

    /**
     * 写入单条系统通知（幂等）。
     */
    private void saveNotice(Long orderId, Long toUserId, String clientMsgId, String content) {
        if (orderId == null || toUserId == null || Objects.equals(toUserId, SYSTEM_USER_ID)) {
            return;
        }

        Message message = new Message();
        message.setOrderId(orderId);
        message.setFromUserId(SYSTEM_USER_ID);
        message.setToUserId(toUserId);
        message.setClientMsgId(clientMsgId);
        message.setContent(content);
        message.setRead(false);
        message.setCreateTime(LocalDateTime.now());

        try {
            messageRepository.save(message);
        } catch (DuplicateKeyException e) {
            log.info("system notice duplicated, orderId={}, toUserId={}, clientMsgId={}", orderId, toUserId, clientMsgId);
        }
    }
}

