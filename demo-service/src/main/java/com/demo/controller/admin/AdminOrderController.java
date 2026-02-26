package com.demo.controller.admin;

import com.demo.context.BaseContext;
import com.demo.dto.admin.AdminOrderDTO;
import com.demo.dto.admin.AdminOrderQueryDTO;
import com.demo.dto.admin.OrderFlagRequest;
import com.demo.entity.OrderFlag;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderFlagMapper;
import com.demo.mapper.OrderMapper;
import com.demo.security.InputSecurityGuard;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Day13 Step7 - 管理员订单接口
 */
@RestController
@RequestMapping("/admin/orders")
@Api(tags = "管理员订单接口")
@Slf4j
@Validated
public class AdminOrderController {

    /**
     * Day18 P3-S2：管理端订单排序字段白名单。
     *
     * 设计说明：
     * 1) 仅放行 SQL 中明确分支支持的字段；
     * 2) 非白名单字段直接失败，避免“兜底排序”掩盖注入探测；
     * 3) 控制器层先收口，再进入 Mapper 动态排序分支。
     */
    private static final Set<String> ORDER_SORT_FIELD_WHITELIST =
            new HashSet<>(Arrays.asList("createTime", "payTime"));

    @Autowired
    private OrderFlagMapper orderFlagMapper;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 管理端分页查询订单列表。
     * GET /admin/orders?page=1&pageSize=10&status=paid&startTime=2026-01-01 00:00:00&endTime=2026-01-31 23:59:59
     */
    @GetMapping
    public Result<PageResult<AdminOrderDTO>> listOrders(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "createTime") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) {
        AdminOrderQueryDTO query = new AdminOrderQueryDTO();
        query.setPage(page == null || page < 1 ? 1 : page);
        query.setPageSize(pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100));
        query.setStatus(status);
        // Day18 P3-S2：排序参数统一做“白名单 + 方向枚举”校验后再入查询对象。
        query.setSortField(InputSecurityGuard.normalizeSortField(sortField, ORDER_SORT_FIELD_WHITELIST, "createTime"));
        query.setSortOrder(InputSecurityGuard.normalizeSortOrder(sortOrder, "desc"));
        query.setStartTime(parseDateTime(startTime));
        query.setEndTime(parseDateTime(endTime));

        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<AdminOrderDTO> list = orderMapper.listAdminOrders(query);
        PageInfo<AdminOrderDTO> pageInfo = new PageInfo<>(list);
        PageResult<AdminOrderDTO> result = new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
        return Result.success(result);
    }

    /**
     * 给订单打标记。
     * POST /admin/orders/{orderId}/flags
     */
    @PostMapping("/{orderId}/flags")
    public Result<String> flagOrder(
            @PathVariable @Min(value = 1, message = "订单 ID 必须大于0") Long orderId,
            @Validated @RequestBody OrderFlagRequest request) {
        // Day18 P3-S2：标记类型/备注属于可回显文本，统一经过纯文本守卫后再落库。
        String safeType = InputSecurityGuard.normalizePlainText(request.getType(), "标记类型", 32, true);
        String safeRemark = InputSecurityGuard.normalizePlainText(request.getRemark(), "备注", 200, false);
        log.info("管理员标记异常订单: orderId={}, type={}", orderId, safeType);

        // 幂等检查: 同 orderId + type 不重复
        OrderFlag existing = orderFlagMapper.selectByOrderIdAndType(orderId, safeType);
        if (existing != null) {
            return Result.success("订单已存在该类型标记");
        }

        OrderFlag flag = new OrderFlag();
        flag.setOrderId(orderId);
        flag.setType(safeType);
        flag.setRemark(safeRemark);
        flag.setCreatedBy(BaseContext.getCurrentId());

        try {
            orderFlagMapper.insertOrderFlag(flag);
        } catch (DuplicateKeyException e) {
            log.warn("订单标记唯一键冲突: orderId={}, type={}", orderId, safeType);
            return Result.success("订单已存在该类型标记");
        }

        log.info("订单标记成功: flagId={}", flag.getId());
        return Result.success("标记成功");
    }

    /**
     * 解析日期时间字符串（支持 ISO 和 yyyy-MM-dd HH:mm:ss）。
     */
    private LocalDateTime parseDateTime(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String s = input.trim();
        try {
            return LocalDateTime.parse(s);
        } catch (Exception ignored) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(s, fmt);
            } catch (Exception e) {
                throw new BusinessException("时间格式错误，需为 yyyy-MM-dd HH:mm:ss 或 ISO-8601");
            }
        }
    }
}

