package com.demo.mapper;

import com.demo.entity.OrderShipTimeoutTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货超时任务 Mapper
 *
 * 职责：
 * 1) 创建/查询超时任务
 * 2) 拉取到期待处理任务
 * 3) 状态流转（DONE/CANCELLED）
 * 4) 失败重试调度
 */
@Mapper
public interface OrderShipTimeoutTaskMapper {

    /**
     * 幂等插入任务（依赖 uk(order_id) + INSERT IGNORE）
     *
     * @param task 任务实体
     * @return 影响行数：1=新插入，0=已存在
     */
    int insertIgnore(OrderShipTimeoutTask task);

    /**
     * 根据订单ID查询任务
     *
     * @param orderId 订单ID
     * @return 任务实体（不存在返回 null）
     */
    OrderShipTimeoutTask selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 拉取到期且可执行的待处理任务
     *
     * 规则：
     * - status = PENDING
     * - deadline_time <= now
     * - next_retry_time 为空或已到期
     */
    List<OrderShipTimeoutTask> listDuePending(@Param("limit") int limit);

    /**
     * 标记任务完成（仅 PENDING -> DONE）
     */
    int markDone(@Param("id") Long id);

    /**
     * 标记任务取消（仅 PENDING -> CANCELLED）
     * 用于“订单已发货/已完成，无需超时处理”的场景。
     */
    int markCancelled(@Param("id") Long id);

    /**
     * 标记任务失败并安排下次重试
     */
    int markRetry(@Param("id") Long id,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime,
                  @Param("lastError") String lastError);

    /**
     * 管理端查询任务列表（用于排障/观察状态）。
     *
     * @param orderId 可选：按订单过滤
     * @param status 可选：按任务状态过滤
     * @param limit 返回上限
     */
    List<OrderShipTimeoutTask> listForAdmin(@Param("orderId") Long orderId,
                                            @Param("status") String status,
                                            @Param("limit") int limit);

    /**
     * 管理端“立即重试”：仅对 PENDING 任务清空 next_retry_time。
     *
     * 说明：
     * - 发货超时任务没有 FAILED 状态，失败后仍保持 PENDING 并延迟重试。
     * - 该方法用于人工把任务提前到“立刻可执行”状态。
     */
    int triggerNow(@Param("id") Long id);
}
