package com.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 信用分统计查询 Mapper。
 * 仅负责聚合统计读取，不处理业务规则。
 */
@Mapper
public interface CreditStatMapper {

    /**
     * 统计用户作为买家完成订单数量。
     */
    Long countCompletedAsBuyer(@Param("userId") Long userId);

    /**
     * 统计用户作为卖家完成订单数量。
     */
    Long countCompletedAsSeller(@Param("userId") Long userId);

    /**
     * 统计用户作为买家取消订单数量。
     */
    Long countCancelledAsBuyer(@Param("userId") Long userId);

    /**
     * 统计用户违规扣减信用分总和。
     */
    Integer sumViolationCreditDelta(@Param("userId") Long userId);

    /**
     * 统计用户当前有效封禁次数。
     */
    Integer countActiveBans(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 统计管理员手工调整信用分总和。
     */
    Integer sumAdminAdjustDelta(@Param("userId") Long userId);

}
