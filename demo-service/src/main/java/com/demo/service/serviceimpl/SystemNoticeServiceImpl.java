package com.demo.service.serviceimpl;

import com.demo.entity.Message;
import com.demo.repository.MessageRepository;
import com.demo.service.SystemNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 系统站内信实现。
 *
 * 关键设计：
 * 1) 直接落 Mongo，避免走 MessageService 的订单会话权限限制。
 * 2) 固定使用 SYSTEM_USER_ID 作为发送人，前端可据此展示“系统通知”。
 * 3) 用 clientMsgId 兜底幂等，重复消费不会生成重复消息。
 */
@Slf4j
@Service
public class SystemNoticeServiceImpl implements SystemNoticeService {

    /** 站内系统发送人 ID（约定值）。 */
    private static final Long SYSTEM_USER_ID = 0L;

    /**
     * Day16 商品治理通知不归属订单会话，统一写到 orderId=0 的系统会话槽位。
     */
    private static final Long SYSTEM_NOTICE_ORDER_ID = 0L;

    @Autowired
    private MessageRepository messageRepository;

    @Override
    public void sendNotice(Long toUserId, String clientMsgId, String content) {
        if (toUserId == null || Objects.equals(toUserId, SYSTEM_USER_ID)) {
            return;
        }
        if (clientMsgId == null || clientMsgId.trim().isEmpty()) {
            return;
        }
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        Message message = new Message();
        message.setOrderId(SYSTEM_NOTICE_ORDER_ID);
        message.setFromUserId(SYSTEM_USER_ID);
        message.setToUserId(toUserId);
        message.setClientMsgId(clientMsgId.trim());
        message.setContent(truncateTo500(content.trim()));
        message.setRead(false);
        message.setCreateTime(LocalDateTime.now());

        try {
            messageRepository.save(message);
        } catch (DuplicateKeyException ex) {
            log.info("幂等命中：action=sendSystemNotice, idemKey=clientMsgId:{}, detail=toUserId={}",
                    clientMsgId, toUserId);
        }
    }

    /**
     * 与 SendMessageRequest 口径保持一致，通知内容限制 500 字符。
     */
    private String truncateTo500(String content) {
        if (content.length() <= 500) {
            return content;
        }
        return content.substring(0, 500);
    }
}
