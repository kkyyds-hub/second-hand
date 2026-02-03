package com.demo.controller.admin;

import com.demo.context.BaseContext;
import com.demo.dto.admin.OrderFlagRequest;
import com.demo.entity.OrderFlag;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderFlagMapper;
import com.demo.result.Result;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

/**
 * Day13 Step7 - 管理员订单接口
 */
@RestController
@RequestMapping("/admin/orders")
@Api(tags = "管理员订单接口")
@Slf4j
@Validated
public class AdminOrderController {

    @Autowired
    private OrderFlagMapper orderFlagMapper;

    /**
     * 标记异常订单
     * POST /admin/orders/{orderId}/flags
     */
    @PostMapping("/{orderId}/flags")
    public Result<String> flagOrder(
            @PathVariable @Min(value = 1, message = "订单ID必须大于0") Long orderId,
            @Validated @RequestBody OrderFlagRequest request) {
        log.info("管理员标记异常订单：orderId={}, type={}", orderId, request.getType());

        // 幂等检查：同 orderId + type 不重复
        OrderFlag existing = orderFlagMapper.selectByOrderIdAndType(orderId, request.getType());
        if (existing != null) {
            return Result.success("订单已存在该类型标记");
        }

        OrderFlag flag = new OrderFlag();
        flag.setOrderId(orderId);
        flag.setType(request.getType());
        flag.setRemark(request.getRemark());
        flag.setCreatedBy(BaseContext.getCurrentId());

        try {
            orderFlagMapper.insertOrderFlag(flag);
        } catch (DuplicateKeyException e) {
            log.warn("订单标记唯一键冲突：orderId={}, type={}", orderId, request.getType());
            return Result.success("订单已存在该类型标记");
        }

        log.info("订单标记成功：flagId={}", flag.getId());
        return Result.success("标记成功");
    }
}
