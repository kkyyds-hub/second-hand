package com.demo.mapper;

import com.demo.entity.OrderRefundTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款任务 Mapper
 *
 * 职责：
 * 1) 幂等创建退款任务
 * 2) 拉取待处理/可重试任务
 * 3) 标记成功或失败重试
 */
@Mapper
public interface OrderRefundTaskMapper {

    /**
     * 幂等插入退款任务（依赖唯一键防重复）
     *
     * @return 1=插入成功，0=重复被忽略
     */
    int insertIgnore(OrderRefundTask task);

    /**
     * 根据订单ID+退款类型查询任务
     */
    OrderRefundTask selectByOrderIdAndType(@Param("orderId") Long orderId,
                                           @Param("refundType") String refundType);

    /**
     * 拉取待处理任务（PENDING 或 FAILED 且到达重试时间）
     */
    List<OrderRefundTask> listRunnable(@Param("limit") int limit);

    /**
     * 标记退款成功
     */
    int markSuccess(@Param("id") Long id);

    /**
     * 标记退款失败并设置下次重试
     */
    int markFail(@Param("id") Long id,
                 @Param("nextRetryTime") LocalDateTime nextRetryTime,
                 @Param("failReason") String failReason);

    /**
     * 管理端查询任务列表（用于排障/观察状态）。
     *
     * @param orderId 可选：按订单过滤
     * @param status 可选：按任务状态过滤
     * @param limit 返回上限
     */
    List<OrderRefundTask> listForAdmin(@Param("orderId") Long orderId,
                                       @Param("status") String status,
                                       @Param("limit") int limit);

    /**
     * 管理端重置任务：FAILED -> PENDING。
     *
     * 重置内容：
     * - status 置为 PENDING
     * - 清空 fail_reason / next_retry_time
     */
    int resetFailedToPending(@Param("id") Long id);
}
