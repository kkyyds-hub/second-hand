package com.demo.mapper;

import com.demo.entity.OrderShipReminderTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货提醒任务 Mapper。
 *
 * 职责：
 * 1) 支付成功后预生成提醒任务（H24/H6/H1）
 * 2) Job 批量抢占到期任务并推进状态机
 * 3) 支持失败重试、卡死回收、管理端排障查询
 */
@Mapper
public interface OrderShipReminderTaskMapper {

    /**
     * 幂等插入任务（依赖 UNIQUE(order_id, level)）。
     *
     * @param task 任务实体
     * @return 1=插入成功，0=已存在（幂等命中）
     */
    int insertIgnore(OrderShipReminderTask task);

    /**
     * 批量抢占到期任务：PENDING/FAILED -> RUNNING。
     *
     * @param runningAt 本轮运行时间戳（用于回查本轮被抢占任务）
     * @param limit     抢占上限
     */
    int markRunningBatch(@Param("runningAt") LocalDateTime runningAt,
                         @Param("limit") int limit);

    /**
     * 查询本轮抢占到的 RUNNING 任务。
     */
    List<OrderShipReminderTask> listRunningByRound(@Param("runningAt") LocalDateTime runningAt, @Param("limit") int limit);

    /**
     * 查询超时 RUNNING 任务（用于“卡死回收”）。
     */
    List<OrderShipReminderTask> listStaleRunning(@Param("staleBefore") LocalDateTime staleBefore,
                                                 @Param("limit") int limit);

    /**
     * 标记成功：RUNNING -> SUCCESS。
     */
    int markSuccess(@Param("id") Long id,
                    @Param("sentAt") LocalDateTime sentAt,
                    @Param("clientMsgId") String clientMsgId);

    /**
     * 标记失败：RUNNING -> FAILED，并重置为后续可重试状态。
     */
    int markFail(@Param("id") Long id,
                 @Param("nextRemindTime") LocalDateTime nextRemindTime,
                 @Param("lastError") String lastError);

    /**
     * 取消任务：RUNNING -> CANCELLED（订单已终态，无需提醒）。
     */
    int markCancelled(@Param("id") Long id);

    /**
     * 管理端任务查询。
     */
    List<OrderShipReminderTask> listForAdmin(@Param("orderId") Long orderId,
                                             @Param("status") String status,
                                             @Param("limit") int limit);

    /**
     * 管理端“立即重跑”：PENDING/FAILED -> PENDING 且 remind_time=NOW()。
     */
    int triggerNow(@Param("id") Long id);
}
