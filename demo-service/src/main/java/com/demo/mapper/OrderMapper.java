package com.demo.mapper;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.seller.SellerOrderCountDTO;
import com.demo.dto.statistics.OrderGmvStatsDTO;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    List<BuyerOrderSummary> listBuyerOrders(
            @Param("buyerId") Long buyerId,
            @Param("query") PageQueryDTO pageQueryDTO
    );

    List<SellerOrderSummary> listSellerOrders(
            @Param("currentUserId") Long currentUserId,
            @Param("query") PageQueryDTO pageQueryDTO
    );

    OrderDetail getOrderDetail(@Param("orderId") Long orderId,
                               @Param("currentUserId") Long currentUserId
    );

    int updateForShipping(Order order);

    int updateForConfirm(Order orderToUpdate);

    int insertOrder( Order order);

    int insertOrderItem( OrderItem Item);

    int markProductSoldIfOnSale(@Param("productId") Long productId);

    int updateForPay(@Param("orderId") Long orderId,
                     @Param("buyerId") Long buyerId);

    int updateForCancel(@Param("orderId") Long orderId,
                        @Param("buyerId") Long buyerId,
                        @Param("reason") String reason);

    int releaseProductsForOrder(@Param("orderId") Long orderId);

    List<Long> findTimeoutPendingOrderIds(@Param("deadline") LocalDateTime deadline,
                                          @Param("limit") Integer limit);

    int closeTimeoutOrder(@Param("orderId") Long orderId,
                          @Param("deadline") LocalDateTime deadline);

    SellerOrderCountDTO countOrdersBySellerId(@Param("sellerId") Long sellerId);

    Order selectOrderBasicById(@Param("orderId") Long orderId);

    /**
 * 查询订单的商品明细（用于评价等：取第一条得到 product_id）
 */
List<OrderItem> selectItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * Day13 Step2 - 根据订单号查询订单基础信息（用于支付回调）
     */
    Order selectOrderByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Day13 Step2 - 支付回调：pending -> paid（幂等）
     */
    int updateForPayByOrderNo(@Param("orderNo") String orderNo);

    /**
     * Day13 Step7 - 统计指定日期的成交订单量与GMV
     */
    OrderGmvStatsDTO countOrderAndGMVByDate(@Param("date") java.time.LocalDate date);

}
