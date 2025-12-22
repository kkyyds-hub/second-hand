package com.demo.mapper;

import com.demo.dto.base.PageQueryDTO;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    int insertOrder(@Param("order") Order order);

    int insertOrderItem(@Param("item") OrderItem Item);

    int markProductSoldIfOnSale(@Param("productId") Long productId);
}
