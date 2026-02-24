package com.demo.service.serviceimpl;

import com.demo.mapper.MessageOutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 批量状态刷写服务。
 *
 * 说明：
 * - 该服务只负责“状态回写”，不负责消息发送；
 * - 使用事务包裹成功/失败两类更新，避免出现“只更新一半”的中间态。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxBatchStatusService {

    private final MessageOutboxMapper messageOutboxMapper;

    /**
     * 批量回写发送结果（单事务）。
     *
     * @param sentIds       本轮发送成功的 outbox 主键集合
     * @param failIds       本轮发送失败的 outbox 主键集合
     * @param nextRetryTime 失败记录统一写入的下次重试时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void flushPublishResult(List<Long> sentIds, List<Long> failIds, LocalDateTime nextRetryTime) {
        // 先刷成功，再刷失败；两者在同一事务内，要么都成功，要么都回滚
        if (sentIds != null && !sentIds.isEmpty()) {
            int sentRows = messageOutboxMapper.markSentBatch(sentIds);
            if (sentRows != sentIds.size()) {
                log.info("Outbox 批量标记成功存在状态漂移：expectedRows={}, actualRows={}",
                        sentIds.size(), sentRows);
            }
        }
        if (failIds != null && !failIds.isEmpty()) {
            int failRows = messageOutboxMapper.markFailBatch(failIds, nextRetryTime);
            if (failRows != failIds.size()) {
                log.info("Outbox 批量标记失败存在状态漂移：expectedRows={}, actualRows={}, nextRetryTime={}",
                        failIds.size(), failRows, nextRetryTime);
            }
        }
    }
}
