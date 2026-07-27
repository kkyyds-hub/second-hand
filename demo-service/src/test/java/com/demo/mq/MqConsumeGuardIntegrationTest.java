package com.demo.mq;

import com.demo.entity.MqConsumeLog;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.service.MqConsumeGuard;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MqConsumeGuard 统一幂等守卫集成测试。
 *
 * 覆盖：
 * 1) ACQUIRED_NEW：首次插入 PROCESSING 成功
 * 2) ALREADY_COMPLETED：已有 OK，不重新执行业务
 * 3) IN_PROGRESS_RECENT：最近 PROCESSING，不 ACK 为完成
 * 4) RECOVERED_STALE：过期 PROCESSING 被原子接管
 * 5) RETRYABLE_FAILED：FAIL 记录允许重新抢占
 * 6) 并发接管：多线程同时处理同一过期 PROCESSING
 * 7) markSuccess/markFailure 状态转换
 * 8) 进程崩溃模拟：PROCESSING 被恢复
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MqConsumeGuardIntegrationTest {

    private static final String CONSUMER = "TestConsumer";
    private static final String PREFIX = "P6MQA_";

    @Autowired
    private MqConsumeGuard consumeGuard;

    @Autowired
    private MqConsumeLogMapper mqConsumeLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final List<Long> createdLogIds = new ArrayList<>();

    @AfterEach
    void cleanupTestLogs() {
        for (Long id : createdLogIds) {
            jdbcTemplate.update("DELETE FROM mq_consume_log WHERE id = ?", id);
        }
        createdLogIds.clear();
    }

    // ────────────────────────────────────────
    // 9.1 正常首次消费
    // ────────────────────────────────────────

    @Test
    @DisplayName("ACQUIRED_NEW：首次抢占成功，可执行业务")
    void acquireNew() {
        String eventId = PREFIX + UUID.randomUUID();

        MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, ar.type());
        assertNotNull(ar.logId());
        assertTrue(ar.shouldExecute());
        createdLogIds.add(ar.logId());

        // 数据库状态应为 PROCESSING
        MqConsumeLog dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertNotNull(dbRecord);
        assertEquals("PROCESSING", dbRecord.getStatus());

        // 执行业务后标记成功
        consumeGuard.markSuccess(ar.logId());

        // 应变为 OK
        dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("OK", dbRecord.getStatus());
    }

    // ────────────────────────────────────────
    // 9.2 已完成重复投递
    // ────────────────────────────────────────

    @Test
    @DisplayName("ALREADY_COMPLETED：已有 OK 时直接 ACK")
    void alreadyCompleted() {
        String eventId = PREFIX + UUID.randomUUID();

        // 第一次：ACQUIRED_NEW
        MqConsumeGuard.AcquireResult ar1 = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar1.logId());
        consumeGuard.markSuccess(ar1.logId());

        // 第二次：ALREADY_COMPLETED
        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar2.type());
        assertFalse(ar2.shouldExecute());
    }

    // ────────────────────────────────────────
    // 9.3 最近 PROCESSING
    // ────────────────────────────────────────

    @Test
    @DisplayName("IN_PROGRESS_RECENT：最近 PROCESSING 不直接 ACK")
    void inProgressRecent() {
        String eventId = PREFIX + UUID.randomUUID();

        // 第一次插入 PROCESSING（不标记成功）
        MqConsumeGuard.AcquireResult ar1 = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar1.logId());
        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, ar1.type());

        // 第二次：同一 eventId → IN_PROGRESS_RECENT
        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.IN_PROGRESS_RECENT, ar2.type());
        assertFalse(ar2.shouldExecute());

        // 状态仍是 PROCESSING（未被覆盖）
        MqConsumeLog dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("PROCESSING", dbRecord.getStatus());
    }

    // ────────────────────────────────────────
    // 9.4 过期 PROCESSING 恢复
    // ────────────────────────────────────────

    @Test
    @DisplayName("RECOVERED_STALE：过期 PROCESSING 被接管")
    void recoveredStale() {
        String eventId = PREFIX + UUID.randomUUID();

        // 插入 PROCESSING 记录并人工设置 updated_at 为过去
        MqConsumeGuard.AcquireResult ar1 = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar1.logId());

        // 把 updated_at 改到 10 分钟前（超过默认 5 分钟 stale 超时）
        jdbcTemplate.update(
                "UPDATE mq_consume_log SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(10), ar1.logId());

        // 第二次：应能原子接管过期 PROCESSING
        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.RECOVERED_STALE, ar2.type());
        assertTrue(ar2.shouldExecute());
        assertEquals(ar1.logId(), ar2.logId()); // 复用同一 logId

        // 执行业务后标记成功
        consumeGuard.markSuccess(ar2.logId());

        MqConsumeLog dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("OK", dbRecord.getStatus());
    }

    // ────────────────────────────────────────
    // 9.5 并发恢复
    // ────────────────────────────────────────

    @Test
    @DisplayName("并发接管：只有一个实例接管过期 PROCESSING")
    void concurrentRecovery() throws Exception {
        String eventId = PREFIX + UUID.randomUUID();

        // 插入并标记为过期
        MqConsumeGuard.AcquireResult ar1 = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar1.logId());
        jdbcTemplate.update(
                "UPDATE mq_consume_log SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(10), ar1.logId());

        // 10 个线程同时尝试接管
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger executedCount = new AtomicInteger(0);
        AtomicInteger recoveredCount = new AtomicInteger(0);
        Set<Long> executorLogIds = new HashSet<>();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(2, TimeUnit.SECONDS);
                    MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER, eventId);
                    if (ar.shouldExecute()) {
                        executedCount.incrementAndGet();
                        consumeGuard.markSuccess(ar.logId());
                        executorLogIds.add(ar.logId());
                    }
                    if (ar.type() == MqConsumeGuard.AcquireResult.Type.RECOVERED_STALE) {
                        recoveredCount.incrementAndGet();
                    }
                } catch (Exception ignored) {}
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // 只有一个实例成功接管并执行业务
        assertEquals(1, executedCount.get(), "Only one instance should execute business");
        assertEquals(1, executorLogIds.size(), "All should use the same logId");

        // 最终状态 OK
        MqConsumeLog dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("OK", dbRecord.getStatus());
    }

    // ────────────────────────────────────────
    // 9.6 FAIL 重试
    // ────────────────────────────────────────

    @Test
    @DisplayName("RETRYABLE_FAILED：FAIL 记录允许重新抢占")
    void retryableFailed() {
        String eventId = PREFIX + UUID.randomUUID();

        // 第一次：成功插入并标记失败
        MqConsumeGuard.AcquireResult ar1 = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar1.logId());
        consumeGuard.markFailure(ar1.logId());

        // 数据库状态应为 FAIL
        MqConsumeLog dbRecord = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("FAIL", dbRecord.getStatus());

        // 第二次：消息重新投递 → RETRYABLE_FAILED，可以执行
        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.RETRYABLE_FAILED, ar2.type());
        assertTrue(ar2.shouldExecute());

        // 执行成功后标记 OK
        consumeGuard.markSuccess(ar2.logId());

        // 第三次：ALREADY_COMPLETED
        MqConsumeGuard.AcquireResult ar3 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar3.type());
        assertFalse(ar3.shouldExecute());
    }

    // ────────────────────────────────────────
    // 9.7 进程崩溃模拟
    // ────────────────────────────────────────

    @Test
    @DisplayName("崩溃恢复：PROCESSING 崩溃后重投不会直接 ACK")
    void crashRecovery() {
        String eventId = PREFIX + UUID.randomUUID();

        // 模拟：写入 PROCESSING（业务尚未开始），消费者进程崩溃
        MqConsumeLog record = new MqConsumeLog();
        record.setConsumer(CONSUMER);
        record.setEventId(eventId);
        record.setStatus("PROCESSING");
        mqConsumeLogMapper.insert(record);
        createdLogIds.add(record.getId());

        // 消息重新投递 → 不应直接 ACK（旧实现这个场景会 ACK）
        MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER, eventId);

        // 如果是最近 PROCESSING → IN_PROGRESS_RECENT
        // 如果被人工老化 → RECOVERED_STALE
        boolean isCorrect = ar.type() == MqConsumeGuard.AcquireResult.Type.IN_PROGRESS_RECENT
                || ar.type() == MqConsumeGuard.AcquireResult.Type.RECOVERED_STALE;

        assertTrue(isCorrect,
                "Crash recovery should be IN_PROGRESS_RECENT or RECOVERED_STALE, got: " + ar.type());
    }

    // ────────────────────────────────────────
    // 边界测试
    // ────────────────────────────────────────

    @Test
    @DisplayName("不同 eventId 互不干扰")
    void differentEventIds() {
        String e1 = PREFIX + UUID.randomUUID();
        String e2 = PREFIX + UUID.randomUUID();

        MqConsumeGuard.AcquireResult a1 = consumeGuard.acquire(CONSUMER, e1);
        createdLogIds.add(a1.logId());
        MqConsumeGuard.AcquireResult a2 = consumeGuard.acquire(CONSUMER, e2);
        createdLogIds.add(a2.logId());

        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, a1.type());
        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, a2.type());
        assertNotEquals(a1.logId(), a2.logId());
    }

    @Test
    @DisplayName("不同 consumer 名互不干扰")
    void differentConsumers() {
        String eventId = PREFIX + UUID.randomUUID();

        MqConsumeGuard.AcquireResult a1 = consumeGuard.acquire("ConsumerA", eventId);
        createdLogIds.add(a1.logId());
        consumeGuard.markSuccess(a1.logId());

        // 不同 consumer 首次处理同一 eventId → ACQUIRED_NEW
        MqConsumeGuard.AcquireResult a2 = consumeGuard.acquire("ConsumerB", eventId);
        createdLogIds.add(a2.logId());

        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, a2.type());
    }

    @Test
    @DisplayName("markSuccess → OK 后再 acquire 返回 ALREADY_COMPLETED")
    void markSuccessThenReacquire() {
        String eventId = PREFIX + UUID.randomUUID();

        MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar.logId());
        consumeGuard.markSuccess(ar.logId());

        MqConsumeLog db = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("OK", db.getStatus());

        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar2.type());
    }

    @Test
    @DisplayName("markFailure → FAIL 后再 acquire 返回 RETRYABLE_FAILED")
    void markFailureThenReacquire() {
        String eventId = PREFIX + UUID.randomUUID();

        MqConsumeGuard.AcquireResult ar = consumeGuard.acquire(CONSUMER, eventId);
        createdLogIds.add(ar.logId());
        consumeGuard.markFailure(ar.logId());

        MqConsumeLog db = mqConsumeLogMapper.selectByConsumerAndEventId(CONSUMER, eventId);
        assertEquals("FAIL", db.getStatus());

        MqConsumeGuard.AcquireResult ar2 = consumeGuard.acquire(CONSUMER, eventId);
        assertEquals(MqConsumeGuard.AcquireResult.Type.RETRYABLE_FAILED, ar2.type());
    }
}
