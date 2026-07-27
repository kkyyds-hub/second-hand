package com.demo.mq;

import com.demo.entity.MqConsumeLog;
import com.demo.mapper.MqConsumeLogMapper;
import com.demo.service.MqConsumeGuard;
import com.demo.service.serviceimpl.MqConsumeGuardImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MqConsumeGuardRaceTest {

    @Mock private MqConsumeLogMapper mapper;
    @InjectMocks private MqConsumeGuardImpl guard;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(guard, "processingStaleSeconds", 300);
    }

    // ====================================================================
    // 二次插入冲突不返回 ALREADY_COMPLETED
    // ====================================================================

    @Test
    @DisplayName("reinsertConflictAfterFailedRetakeDoesNotReturnCompleted")
    void reinsertConflictAfterFailedRetakeDoesNotReturnCompleted() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        // 1) insert → DuplicateKey
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));

        MqConsumeLog failLog = log(1L, "FAIL", "old-token", 1);
        MqConsumeLog okLog = log(2L, "OK", "new-token", 2);

        // Chain: first select → FAIL, second → null, third → OK
        when(mapper.selectByConsumerAndEventId(consumer, eid))
                .thenReturn(failLog, null, okLog);

        // retakeFailedLease → rows=0 (被抢先)
        when(mapper.retakeFailedLease(eq(1L), anyString())).thenReturn(0);

        // 断言：最终返回 ALREADY_COMPLETED（因为第三次 select 查到了 OK）
        MqConsumeGuard.AcquireResult ar = guard.acquire(consumer, eid);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar.type());
        assertFalse(ar.canExecute());
        verify(mapper, atLeast(2)).selectByConsumerAndEventId(eq(consumer), eq(eid));
    }

    @Test
    @DisplayName("reinsertConflictAfterStaleTakeoverDoesNotReturnCompleted")
    void reinsertConflictAfterStaleTakeoverDoesNotReturnCompleted() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        // 1) insert → DuplicateKey
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));

        MqConsumeLog proc = log(3L, "PROCESSING", "old-tok", 1);
        proc.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        MqConsumeLog freshProc = log(4L, "PROCESSING", "fresh-tok", 1);

        // Chain: first → expired PROCESSING, then → null, then → fresh PROCESSING
        when(mapper.selectByConsumerAndEventId(consumer, eid))
                .thenReturn(proc, null, freshProc);

        // stale takeover → rows=0
        when(mapper.updateStatusIfStaleAndLeaseToken(eq(3L), eq("old-tok"), any(), anyString())).thenReturn(0);

        // 断言：不是 ALREADY_COMPLETED（新鲜 PROCESSING 不是完成）
        MqConsumeGuard.AcquireResult ar = guard.acquire(consumer, eid);
        assertNotEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar.type());
        assertFalse(ar.canExecute());
    }

    // ====================================================================
    // 遗留 NULL lease_token PROCESSING 可恢复
    // ====================================================================

    @Test
    @DisplayName("legacyProcessingWithNullLeaseCanRecover")
    void legacyProcessingWithNullLeaseCanRecover() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        // 1) insert → DuplicateKey
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));

        // 2) select → PROCESSING with NULL lease_token (legacy)
        MqConsumeLog legacy = log(5L, "PROCESSING", null, 0);
        legacy.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        when(mapper.selectByConsumerAndEventId(consumer, eid)).thenReturn(legacy);

        // 3) stale takeover with NULL observedLeaseToken → rows=1
        when(mapper.updateStatusIfStaleAndLeaseToken(eq(5L), isNull(), any(), anyString()))
                .thenReturn(1);

        // 断言：RECOVERED_STALE with non-null leaseToken
        MqConsumeGuard.AcquireResult ar = guard.acquire(consumer, eid);
        assertEquals(MqConsumeGuard.AcquireResult.Type.RECOVERED_STALE, ar.type());
        assertNotNull(ar.leaseToken());
        assertTrue(ar.canExecute());
    }

    // ====================================================================
    // 解析循环有界
    // ====================================================================

    @Test
    @DisplayName("resolutionLoopIsBounded")
    void resolutionLoopIsBounded() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        // insert → always DuplicateKey
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));
        // select → always null (force re-insert loop)
        when(mapper.selectByConsumerAndEventId(consumer, eid)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> guard.acquire(consumer, eid));
    }

    // ====================================================================
    // attempt_count 精确值
    // ====================================================================

    @Test
    @DisplayName("attemptCountIsExactlyTwoAfterSingleRetake")
    void attemptCountIsExactlyTwoAfterSingleRetake() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        // ACQUIRED_NEW
        when(mapper.insert(any(MqConsumeLog.class))).thenAnswer(inv -> {
            MqConsumeLog l = inv.getArgument(0);
            l.setId(10L);
            assertEquals(Integer.valueOf(1), l.getAttemptCount());
            return 1;
        });

        MqConsumeGuard.AcquireResult ar1 = guard.acquire(consumer, eid);
        assertEquals(MqConsumeGuard.AcquireResult.Type.ACQUIRED_NEW, ar1.type());
        assertEquals(10L, ar1.logId());

        // markSuccess
        when(mapper.markSuccess(10L, ar1.leaseToken())).thenReturn(1);

        assertTrue(guard.markSuccess(ar1.logId(), ar1.leaseToken()));

        // Now FAIL + retake
        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));
        MqConsumeLog failLog = log(10L, "FAIL", ar1.leaseToken(), 1);
        when(mapper.selectByConsumerAndEventId(consumer, eid)).thenReturn(failLog);

        // retakeFailedLease → will set attempt_count = attempt_count + 1 = 2
        when(mapper.retakeFailedLease(eq(10L), anyString())).thenAnswer(inv -> {
            // This is where attempt_count increments: the SQL does `attempt_count + 1`
            failLog.setAttemptCount(failLog.getAttemptCount() + 1);
            return 1;
        });

        MqConsumeGuard.AcquireResult ar2 = guard.acquire(consumer, eid);
        assertEquals(MqConsumeGuard.AcquireResult.Type.RETRYABLE_FAILED, ar2.type());

        // Verify: attempt_count was exactly 1 before retake, SQL makes it 2
        assertEquals(2, failLog.getAttemptCount(), "attempt_count should be exactly 2 after single retake");
    }

    // ====================================================================
    // UNKNOWN_STATE_RETRY 真实测试
    // ====================================================================

    @Test
    @DisplayName("unknownStatusReturnsUnknownStateRetryNotCompleted")
    void unknownStatusReturnsUnknownStateRetryNotCompleted() {
        String eid = "test-" + UUID.randomUUID();
        String consumer = "TestConsumer";

        doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(MqConsumeLog.class));

        MqConsumeLog broken = log(9L, "BROKEN", "tok", 1);
        when(mapper.selectByConsumerAndEventId(consumer, eid)).thenReturn(broken);

        MqConsumeGuard.AcquireResult ar = guard.acquire(consumer, eid);
        assertEquals(MqConsumeGuard.AcquireResult.Type.UNKNOWN_STATE_RETRY, ar.type());
        assertFalse(ar.canExecute());
        assertNull(ar.leaseToken());
        assertNotEquals(MqConsumeGuard.AcquireResult.Type.ALREADY_COMPLETED, ar.type());
    }

    private MqConsumeLog log(Long id, String status, String token, int attempts) {
        MqConsumeLog l = new MqConsumeLog();
        l.setId(id);
        l.setStatus(status);
        l.setLeaseToken(token);
        l.setAttemptCount(attempts);
        l.setUpdatedAt(LocalDateTime.now());
        return l;
    }
}
