package com.demo.mapper;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.admin.AdminOrderQueryDTO;
import com.demo.dto.seller.SellerOrderCountDTO;
import com.demo.dto.statistics.OrderGmvStatsDTO;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.dto.admin.AdminOrderDTO;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
/**
 * OrderMapper 接口。
 */
public interface OrderMapper {
    /**
     * 查询买家订单分页列表。
     */
    List<BuyerOrderSummary> listBuyerOrders(
            @Param("buyerId") Long buyerId,
            @Param("query") PageQueryDTO pageQueryDTO
    );

    /**
     * 查询卖家订单分页列表。
     */
    List<SellerOrderSummary> listSellerOrders(
            @Param("currentUserId") Long currentUserId,
            @Param("query") PageQueryDTO pageQueryDTO
    );

    /**
     * 管理端分页查询订单列表。
     */
    List<AdminOrderDTO> listAdminOrders(@Param("query") AdminOrderQueryDTO query);

    /**
     * 查询订单详情（买家或卖家可见）。
     */
    OrderDetail getOrderDetail(@Param("orderId") Long orderId,
                               @Param("currentUserId") Long currentUserId
    );

    /**
     * 卖家发货：更新物流字段并执行 paid -> shipped。
     */
    int updateForShipping(Order order);

    /**
     * 买家确认收货：执行 shipped -> completed。
     */
    int updateForConfirm(Order orderToUpdate);

    /**
     * 新增订单主记录。
     */
    int insertOrder( Order order);

    /**
     * 新增订单商品明细。
     */
    int insertOrderItem( OrderItem Item);

    /**
     * 商品状态流转：on_sale -> sold（条件更新）。
     */
    int markProductSoldIfOnSale(@Param("productId") Long productId);

    /**
     * 订单支付成功：pending -> paid（条件更新）。
     */
    int updateForPay(@Param("orderId") Long orderId,
                     @Param("buyerId") Long buyerId);

    /**
     * 买家取消订单：pending -> cancelled（条件更新）。
     */
    int updateForCancel(@Param("orderId") Long orderId,
                        @Param("buyerId") Long buyerId,
                        @Param("reason") String reason);

    /**
     * 释放订单占用商品：sold -> on_sale（条件更新）。
     */
    int releaseProductsForOrder(@Param("orderId") Long orderId);

    /**
     * 查询超时未支付订单 ID 列表。
     */
    List<Long> findTimeoutPendingOrderIds(@Param("deadline") LocalDateTime deadline,
                                          @Param("limit") Integer limit);

    /**
     * 关闭超时未支付订单。
     */
    int closeTimeoutOrder(@Param("orderId") Long orderId,
                          @Param("deadline") LocalDateTime deadline);

    /**
     * 统计卖家订单数量分布。
     */
    SellerOrderCountDTO countOrdersBySellerId(@Param("sellerId") Long sellerId);

    /**
     * 按订单 ID 查询基础信息。
     */
    Order selectOrderBasicById(@Param("orderId") Long orderId);

    /**
     * Step7：提醒任务专用查询（只取提醒所需字段，避免复用方法字段缺失）。
     */
    Order selectOrderForReminder(@Param("orderId") Long orderId);

    /**
     * 查询订单商品明细（用于评价等场景）。
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

    /**
     * day15：关闭“已支付但超时未发货”订单（paid -> cancelled）
     *
     * 条件更新口径（非常关键）：
     * 1) 订单当前状态必须是 paid
     * 2) pay_time 必须 <= deadline（支付时间 + 48h）
     * 3) 仅满足条件时才能更新为 cancelled，防止误关已发货订单
     *
     * @param orderId 订单 ID
     * @param deadline 发货截止时间（通常来自任务表 deadline_time）
     * @return 影响行数（1=成功关单，0=状态不满足或已被并发修改）
     */
    int closeShipTimeoutOrder(@Param("orderId") Long orderId,
                              @Param("deadline") LocalDateTime deadline);

}
