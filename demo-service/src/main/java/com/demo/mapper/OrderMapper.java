package com.demo.mapper;

import com.demo.dto.base.PageQueryDTO;
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



}
