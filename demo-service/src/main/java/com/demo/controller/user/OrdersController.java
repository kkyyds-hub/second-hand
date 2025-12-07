package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.base.PageQueryDTO;
import com.demo.result.Result;
import com.demo.service.OrderService;
import com.demo.vo.BuyerOrderSummary;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@Api(tags = "用户订单接口")
@Slf4j
public class OrdersController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/buy")
    public Result<PageInfo<BuyerOrderSummary>> buy(@Validated PageQueryDTO pageQueryDTO) {
        log.info("用户购买商品: {}", pageQueryDTO);
        Long currentUserId = BaseContext.getCurrentId();
        PageInfo<BuyerOrderSummary> pageInfo = orderService.buy(pageQueryDTO, currentUserId);
        return Result.success(pageInfo);
    }
}
