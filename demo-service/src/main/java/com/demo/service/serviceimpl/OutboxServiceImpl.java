package com.demo.service.serviceimpl;

import com.demo.entity.MessageOutbox;
import com.demo.mapper.MessageOutboxMapper;
import com.demo.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day14 - Outbox 服务实现
 */
@Slf4j
@Service
public class OutboxServiceImpl implements OutboxService {

    @Autowired
    private MessageOutboxMapper messageOutboxMapper;

    /**
     * 在业务主事务中写入 Outbox 事件。
     *
     * 约束：
     * 1) 使用 MANDATORY，要求调用方已处于事务内；
     * 2) 与业务数据同提交/同回滚，避免“主数据成功但消息丢失”。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void save(MessageOutbox outbox) {
        messageOutboxMapper.insert(outbox);
        log.info("Outbox 入库成功：eventId={}, eventType={}, bizId={}, status={}",
                outbox.getEventId(), outbox.getEventType(), outbox.getBizId(), outbox.getStatus());
    }
}
