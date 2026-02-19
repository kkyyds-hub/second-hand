package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.message.MessageDTO;
import com.demo.dto.message.SendMessageRequest;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.MessageService;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

/**
 * Day13 Step3 - 站内消息接口
 */
@RestController
@RequestMapping("/user/messages")
@Api(tags = "用户站内消息接口")
@Slf4j
@Validated
public class MessageController {

    @Autowired
    private MessageService messageService;

    /**
     * 发送消息
     * POST /user/messages/orders/{orderId}
     */
    @PostMapping("/orders/{orderId}")
    public Result<MessageDTO> sendMessage(
            @PathVariable @Min(value = 1, message = "订单 ID 必须大于0") Long orderId,
            @Validated @RequestBody SendMessageRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("发送消息：orderId={}, from={}, to={}", orderId, currentUserId, request.getToUserId());

        MessageDTO message = messageService.sendMessage(orderId, currentUserId, request);
        return Result.success(message);
    }

    /**
     * 拉取订单会话消息（分页）
     * GET /user/messages/orders/{orderId}?page=1&pageSize=20
     */
    @GetMapping("/orders/{orderId}")
    public Result<PageResult<MessageDTO>> listMessages(
            @PathVariable @Min(value = 1, message = "订单 ID 必须大于0") Long orderId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("拉取消息：orderId={}, userId={}, page={}, pageSize={}", orderId, currentUserId, page, pageSize);

        PageResult<MessageDTO> result = messageService.listMessages(orderId, currentUserId, page, pageSize);
        return Result.success(result);
    }

    /**
     * 未读数
     * GET /user/messages/unread-count
     */
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("查询未读数：userId={}", currentUserId);

        Long count = messageService.getUnreadCount(currentUserId);
        return Result.success(count);
    }

    /**
     * 标记订单会话已读
     * PUT /user/messages/orders/{orderId}/read
     */
    @PutMapping("/orders/{orderId}/read")
    public Result<String> markAsRead(
            @PathVariable @Min(value = 1, message = "订单 ID 必须大于0") Long orderId) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("标记已读：orderId={}, userId={}", orderId, currentUserId);

        String msg = messageService.markAsRead(orderId, currentUserId);
        return Result.success(msg);
    }
}

