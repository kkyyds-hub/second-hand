package com.demo.mapper;

import com.demo.entity.MessageOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Day14 - 事务性外箱 Mapper
 * <p>
 * 作用：操作 message_outbox 表
 * 1) 入库消息
 * 2) 拉取待发送消息
 * 3) 标记成功/失败
 */
@Mapper
public interface MessageOutboxMapper {

    /**
     * 插入一条 Outbox 消息（在业务事务内调用）
     *
     * @param outbox 外箱消息实体
     * @return 插入影响行数
     */
    int insert(MessageOutbox outbox);

    /**
     * 根据事件 ID查询（用于幂等检查）
     *
     * @param eventId 事件唯一 ID
     * @return 外箱记录（可能为 null）
     */
    MessageOutbox selectByEventId(@Param("eventId") String eventId);

    /**
     * 拉取待发送消息
     * 规则：
     * - status = NEW 或 FAIL
     * - nextRetryTime 为空 或 nextRetryTime <= 当前时间
     * - 按 id ASC 返回前 limit（保语义）
     * - SQL 内部采用分支限流后再合并排序，收口 filesort 影响范围
     *
     * @param limit 拉取条数
     * @return 待发送消息列表
     */
    List<MessageOutbox> listPending(@Param("limit") int limit);

    /**
     * 标记发送成功
     *
     * @param id 外箱表主键
     * @return 更新行数
     */
    int markSent(@Param("id") Long id);

    /**
     * 批量标记发送成功。
     *
     * 约束：
     * - 仅更新状态为 NEW/FAIL 的记录，避免重复刷写 SENT 记录。
     *
     * @param ids 外箱表主键集合
     * @return 更新行数
     */
    int markSentBatch(@Param("ids") List<Long> ids);

    /**
     * 标记发送失败并设置下次重试时间
     *
     * @param id            外箱表主键
     * @param nextRetryTime 下次可重试时间
     * @return 更新行数
     */
    int markFail(@Param("id") Long id,
                 @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 批量标记发送失败并设置下次重试时间。
     *
     * 约束：
     * - 仅更新状态为 NEW/FAIL 的记录；
     * - retry_count 在 SQL 中自增 1，保持与单条失败逻辑一致。
     *
     * @param ids 外箱表主键集合
     * @param nextRetryTime 下次可重试时间
     * @return 更新行数
     */
    int markFailBatch(@Param("ids") List<Long> ids,
                      @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 统计 Outbox 状态数量（用于监控）
     *
     * @param status 状态：NEW / SENT / FAIL
     * @return 数量
     */
    int countByStatus(@Param("status") String status);

    /**
     * 统计指定状态的累计重试次数（用于监控）
     *
     * @param status 状态：NEW / SENT / FAIL
     * @return 重试次数总和
     */
    int sumRetryCountByStatus(@Param("status") String status);

    /**
     * 人工补偿：按 eventId 立即重试。
     *
     * 说明：
     * - 仅对 NEW/FAIL 状态生效；
     * - 通过清空 next_retry_time，让该事件在下一轮调度立即可被拉取。
     *
     * @param eventId 事件 ID
     * @return 更新行数
     */
    int triggerNowByEventId(@Param("eventId") String eventId);

}
