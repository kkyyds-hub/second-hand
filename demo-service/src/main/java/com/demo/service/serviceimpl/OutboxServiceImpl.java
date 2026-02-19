package com.demo.service.serviceimpl;

import com.demo.entity.MessageOutbox;
import com.demo.mapper.MessageOutboxMapper;
import com.demo.service.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Day14 - Outbox 服务实现
 */
@Slf4j
@Service
public class OutboxServiceImpl implements OutboxService {

    @Autowired
    private MessageOutboxMapper messageOutboxMapper;

    /**
     * 创建或新增相关数据。
     */
    @Override
    public void save(MessageOutbox outbox) {
        messageOutboxMapper.insert(outbox);
        log.info("Outbox saved, eventId={}, type={}", outbox.getEventId(), outbox.getEventType());
    }
}
