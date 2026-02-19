package com.demo.mapper;

import com.demo.entity.OrderFlag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Day13 Step7 - 订单异常标记 Mapper
 */
@Mapper
public interface OrderFlagMapper {

    /**
     * 插入订单标记（唯一键冲突时会抛异常）
     */
    int insertOrderFlag(OrderFlag orderFlag);

    /**
     * 根据订单 ID和类型查询标记（幂等检查）
     */
    OrderFlag selectByOrderIdAndType(@Param("orderId") Long orderId, @Param("type") String type);
}
