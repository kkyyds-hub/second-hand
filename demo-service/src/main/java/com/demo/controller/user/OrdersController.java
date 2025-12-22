package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.result.Result;
import com.demo.service.OrderService;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageInfo;
import com.demo.result.PageResult;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/orders")
@Api(tags = "用户订单接口")
@Slf4j
public class OrdersController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/buy")
    public Result<PageResult<BuyerOrderSummary>> buy(@Validated PageQueryDTO pageQueryDTO) {
        log.info("用户购买商品: {}", pageQueryDTO);
        Long currentUserId = BaseContext.getCurrentId();
        PageResult<BuyerOrderSummary> pageResult = orderService.buy(pageQueryDTO, currentUserId);
        return Result.success(pageResult);
    }


    @GetMapping("/sell")
    public Result<PageResult<SellerOrderSummary>> getSellOrder(@Validated PageQueryDTO pageQueryDTO) {
        log.info("获取用户出售商品: {}", pageQueryDTO);
        Long currentUserId = BaseContext.getCurrentId();

        PageResult<SellerOrderSummary> pageResult = orderService.getSellOrder(pageQueryDTO, currentUserId);
        return Result.success(pageResult);
    }



    @GetMapping("/{orderId}")
    public Result<OrderDetail> getOrder(@PathVariable Long orderId) {
        log.info("获取订单详情: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        OrderDetail orderDetail = orderService.getOrderDetail(orderId, currentUserId);
        return Result.success(orderDetail);
    }

    @PostMapping("/{orderId}/ship")
    public Result<String> ship(@PathVariable Long orderId,
                               @Validated @RequestBody ShipOrderRequest request) {
        log.info("卖家发货: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        orderService.shipOrder(orderId, request, currentUserId);
        return Result.success("发货成功");
    }

    @PostMapping("/{orderId}/confirm")
    public Result<String> confirm(@PathVariable Long orderId) {
        log.info("用户确认收货: {}", orderId);
        Long currentUserId = BaseContext.getCurrentId();
        orderService.confirmOrder(orderId, currentUserId);
        return Result.success("确认收货成功");
        //TODO 物流轨迹/评价信息暂未实现，字段返回 null”
    }

    @PostMapping
    public Result<CreateOrderResponse> createOrder(@Validated @RequestBody CreateOrderRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("创建订单: userId={}, request={}", currentUserId, request);

        CreateOrderResponse response = orderService.createOrder(request, currentUserId);
        return Result.success(response);
    }
}
