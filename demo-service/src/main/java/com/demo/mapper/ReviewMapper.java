package com.demo.mapper;

import com.demo.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * ReviewMapper 接口。
 */
public interface ReviewMapper {

    /**
     * 插入评价（可能触发唯一键冲突）
     */
    int insertReview(Review review);

    /**
     * 按 order_id + role 查询是否已存在评价（应用层幂等）
     */
    Review selectByOrderIdAndRole(@Param("orderId") Long orderId, 
                                   @Param("role") Integer role);

    /**
     * 分页查询买家发出的评价（按 buyer_id + is_deleted，已由 PageHelper 处理分页）
     */
    List<Review> listByBuyerId(@Param("buyerId") Long buyerId);

    /**
     * 分页查询商品评价列表（按 product_id + is_deleted，按 create_time DESC）
     */
    List<Review> listByProductId(@Param("productId") Long productId);

    /**
     * Day13 Step4 - 防刷：统计买家在指定时间后创建的评价数量
     */
    int countByBuyerIdSince(@Param("buyerId") Long buyerId, 
                            @Param("since") java.time.LocalDateTime since);
}
