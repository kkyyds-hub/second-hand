package com.demo.service;

import com.demo.entity.MessageOutbox;

/**
 * Day14 - Outbox 服务接口
 */
public interface OutboxService {

    /**
     * 保存 Outbox 消息（事务内调用）
     */
    void save(MessageOutbox outbox);
}
