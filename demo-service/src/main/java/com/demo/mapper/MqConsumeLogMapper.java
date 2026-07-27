package com.demo.mapper;

import com.demo.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * MQ 消费幂等日志 Mapper。
 *
 * P6-MQ-A-F1：新增租约令牌、尝试次数和条件状态更新方法。
 */
@Mapper
public interface MqConsumeLogMapper {

    /** 根据 consumer + eventId 查询 */
    MqConsumeLog selectByConsumerAndEventId(@Param("consumer") String consumer,
                                            @Param("eventId") String eventId);

    /** 插入消费日志（含 lease_token 和 attempt_count） */
    int insert(MqConsumeLog log);

    /**
     * 租约验证成功：status='PROCESSING' + lease_token 匹配 → OK。
     * @return 1 = 成功，0 = 租约不匹配（已被接管）
     */
    int markSuccess(@Param("id") Long id,
                    @Param("leaseToken") String leaseToken);

    /**
     * 租约验证失败：status='PROCESSING' + lease_token 匹配 → FAIL + last_error。
     * @return 1 = 成功，0 = 租约不匹配
     */
    int markFailure(@Param("id") Long id,
                    @Param("leaseToken") String leaseToken,
                    @Param("error") String error);

    /**
     * 原子接管过期 PROCESSING：需要 status='PROCESSING' + lease_token 匹配 + updated_at 过期。
     * 成功后写入新 leaseToken，attempt_count +1。
     * @return 1 = 接管成功，0 = 已被抢占
     */
    int updateStatusIfStaleAndLeaseToken(@Param("id") Long id,
                                          @Param("observedLeaseToken") String observedLeaseToken,
                                          @Param("staleBefore") LocalDateTime staleBefore,
                                          @Param("newLeaseToken") String newLeaseToken);

    /**
     * 原子重新抢占 FAIL：status='FAIL' → PROCESSING + 新租约。
     * @return 1 = 抢占成功，0 = 已被其他人抢占
     */
    int retakeFailedLease(@Param("id") Long id,
                          @Param("newLeaseToken") String newLeaseToken);
}
