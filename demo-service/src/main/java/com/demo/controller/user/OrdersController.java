package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.CancelOrderRequest;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.LogisticsService;
import com.demo.service.OrderService;
import com.demo.service.UserService;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.OrderLogisticsVO;
import com.demo.vo.order.SellerOrderSummary;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Min;

/**
 * 用户订单接口。
 */
@Validated
@RestController
@RequestMapping("/user/orders")
@Api(tags = "用户订单接口")
@Slf4j
public class OrdersController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private LogisticsService logisticsService;

    /**
     * 分页查询当前用户买入订单。
     */
    @GetMapping("/buy")
    public Result<PageResult<BuyerOrderSummary>> buy(@Validated PageQueryDTO pageQueryDTO) {
        log.info("查询用户买入订单: {}", pageQueryDTO);
        Long currentUserId = BaseContext.getCurrentId();
        PageResult<BuyerOrderSummary> pageResult = orderService.buy(pageQueryDTO, currentUserId);
        return Result.success(pageResult);
    }

    /**
     * 分页查询当前用户卖出订单。
     */
    @GetMapping("/sell")
    public Result<PageResult<SellerOrderSummary>> getSellOrder(@Validated PageQueryDTO pageQueryDTO) {
        log.info("查询用户卖出订单: {}", pageQueryDTO);
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        PageResult<SellerOrderSummary> pageResult = orderService.getSellOrder(pageQueryDTO, currentUserId);
        return Result.success(pageResult);
    }

    /**
     * 查询订单详情。
     */
    @GetMapping("/{orderId}")
    public Result<OrderDetail> getOrder(@PathVariable Long orderId) {
        log.info("获取订单详情: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        OrderDetail orderDetail = orderService.getOrderDetail(orderId, currentUserId);
        return Result.success(orderDetail);
    }

    /**
     * 查询订单物流信息（买卖双方均可访问，权限由 Service 校验）。
     */
    @GetMapping("/{orderId}/logistics")
    public Result<OrderLogisticsVO> getOrderLogistics(@PathVariable Long orderId) {
        log.info("获取订单物流: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        OrderLogisticsVO logisticsVO = logisticsService.getOrderLogistics(orderId, currentUserId);
        return Result.success(logisticsVO);
    }

    /**
     * 卖家发货。
     */
    @PostMapping("/{orderId}/ship")
    public Result<String> ship(@PathVariable Long orderId,
                               @Validated @RequestBody ShipOrderRequest request) {
        log.info("卖家发货: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        userService.requireSeller(currentUserId);
        String msg = orderService.shipOrder(orderId, request, currentUserId);
        return Result.success(msg);
    }

    /**
     * 买家确认收货。
     */
    @PostMapping("/{orderId}/confirm-receipt")
    public Result<String> confirm(@PathVariable Long orderId) {
        log.info("用户确认收货: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        String msg = orderService.confirmOrder(orderId, currentUserId);
        return Result.success(msg);
    }

    /**
     * 创建订单。
     */
    @PostMapping
    public Result<CreateOrderResponse> createOrder(@Validated @RequestBody CreateOrderRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("创建订单: userId={}, request={}", currentUserId, request);
        CreateOrderResponse response = orderService.createOrder(request, currentUserId);
        return Result.success(response);
    }

    /**
     * 买家支付订单。
     */
    @PostMapping("/{orderId}/pay")
    public Result<String> pay(@PathVariable Long orderId) {
        log.info("买家支付订单: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        String msg = orderService.payOrder(orderId, currentUserId);
        return Result.success(msg);
    }

    /**
     * 买家取消订单。
     */
    @PostMapping("/{orderId}/cancel")
    public Result<String> cancel(@PathVariable @Min(value = 1, message = "orderId 必须大于 0") Long orderId,
                                 @Valid @RequestBody(required = false) CancelOrderRequest request) {
        log.info("买家取消订单: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        String msg = orderService.cancelOrder(orderId, request, currentUserId);
        return Result.success(msg);
    }
}
