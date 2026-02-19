package com.demo.service.serviceimpl;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.CancelOrderRequest;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.entity.*;
import com.demo.enumeration.CreditReasonType;
import com.demo.mapper.OrderShipTimeoutTaskMapper;
import com.demo.mq.producer.OrderEventProducer;
import com.demo.enumeration.OrderStatus;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.result.PageResult;
import com.demo.service.CreditService;
import com.demo.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.demo.service.OutboxService;
import com.demo.dto.mq.EventMessage;
import com.demo.dto.mq.OrderCreatedPayload;
import com.demo.dto.mq.OrderEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
/**
 * OrderServiceImpl 业务组件。
 */
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private CreditService creditService;

    @Autowired
    private com.demo.service.PointsService pointsService;

    @Autowired
    private OrderEventProducer orderEventProducer;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private ObjectMapper objectMapper;

    // ====== 在类字段区新增 ======
    @Autowired
    private OrderShipTimeoutTaskMapper orderShipTimeoutTaskMapper;

    /**
     * 发货超时时长（小时）
     * 默认 48，可通过配置覆盖。
     */
    @Value("${order.ship-timeout.hours:48}")
    private int shipTimeoutHours;


    /**
     * 实现接口定义的方法。
     */
    @Override
    public PageResult<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId) {
        pageValidated(pageQueryDTO);
        List<BuyerOrderSummary> list = orderMapper.listBuyerOrders(currentUserId, pageQueryDTO);
        PageInfo<BuyerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize());
    }

    /**
     * 查询并返回相关结果。
     */
    @Override
    public PageResult<SellerOrderSummary> getSellOrder(PageQueryDTO dto, Long uid) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());

        List<SellerOrderSummary> list = orderMapper.listSellerOrders(uid, dto);

        PageInfo<SellerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize());
    }

    /**
     * 查询并返回相关结果。
     */
    @Override
    public OrderDetail getOrderDetail(Long orderId, Long currentUserId) {
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }
        return detail;
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    public String shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId) {
        // 1. 先查订单详情（同时校验与当前用户有关）
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }

        // 2. 额外校验：只能卖家发货
        if (!Objects.equals(detail.getSellerId(), currentUserId)) {
            throw new BusinessException("只有卖家本人可以发货");
        }

        // 3. 用枚举校验状态
        OrderStatus currentStatus = OrderStatus.fromDbValue(detail.getStatus());
        if (currentStatus == null) {
            throw new BusinessException("订单状态异常");
        }

        // 幂等性：如果订单已经发货或已完成，直接返回（不抛异常）
        if (currentStatus == OrderStatus.SHIPPED || currentStatus == OrderStatus.COMPLETED) {
            return "订单已发货，无需重复操作";
        }
        // 状态校验：只能从 paid -> shipped
        if (currentStatus != OrderStatus.PAID) {
            throw new BusinessException("订单当前状态不允许发货，只能对已付款订单发货");
        }
        // 4. 组装 Order 作为更新入参（只放 orders 表需要的字段）
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setSellerId(currentUserId); // 供 SQL where 使用（建议）
        orderToUpdate.setShippingCompany(request.getShippingCompany());
        orderToUpdate.setTrackingNo(request.getTrackingNo());
        orderToUpdate.setShippingRemark(request.getRemark()); // Day5：把备注落库
        orderToUpdate.setStatus(OrderStatus.SHIPPED.getDbValue());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        // 5. 执行更新 + 乐观校验
        int rows = orderMapper.updateForShipping(orderToUpdate);
        if (rows == 1) {
            safePublish("ORDER_STATUS_CHANGED(ship)", () ->
                    orderEventProducer.sendOrderStatusChanged(
                            detail.getOrderId(),
                            detail.getOrderNo(),
                            currentStatus.getDbValue(),
                            OrderStatus.SHIPPED.getDbValue(),
                            currentUserId
                    )
            );

            return "发货成功";
        }

        // rows==0：重新查询最新状态（并发/重复请求）
        OrderDetail latestDetail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (latestDetail == null) {
            throw new BusinessException("操作失败，订单可能已被删除或无权访问");
        }

        OrderStatus latestStatus = OrderStatus.fromDbValue(latestDetail.getStatus());
        if (latestStatus == null) {
            throw new BusinessException("订单状态异常");
        }

        //  幂等：已发货/已完成 -> 成功返回
        if (latestStatus == OrderStatus.SHIPPED || latestStatus == OrderStatus.COMPLETED) {
            return "订单已发货，无需重复操作";
        }

        // 已取消 -> 明确报错
        if (latestStatus == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法发货");
        }


        throw new BusinessException("发货失败，订单状态不允许发货：" + latestDetail.getStatus());
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String confirmOrder(Long orderId, Long currentUserId) {
        // 1. 查询订单详情（买家/卖家都能查到）
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }

        // 2. 必须是买家本人才能确认收货
        if (!Objects.equals(detail.getBuyerId(), currentUserId)) {
            throw new BusinessException("只有买家本人可以确认收货");
        }

        // 3. 状态校验：只能从 shipped -> completed
        OrderStatus currentStatus = OrderStatus.fromDbValue(detail.getStatus());
        if (currentStatus == null) {
            throw new BusinessException("订单状态异常");
        }
        // 幂等性：如果订单已经完成，直接返回（不抛异常）
        if (currentStatus == OrderStatus.COMPLETED) {
            return "订单已确认收货，无需重复操作"; // 稍后改为返回提示信息
        }

        // 状态校验：只能从 shipped -> completed
        if (currentStatus != OrderStatus.SHIPPED) {
            throw new BusinessException("订单当前状态不允许确认收货，只能对已发货订单确认收货");
        }

        // 4. 组装 Order 做更新
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setBuyerId(currentUserId); // 供 SQL where 使用（建议）
        orderToUpdate.setStatus(OrderStatus.COMPLETED.getDbValue());
        orderToUpdate.setCompleteTime(LocalDateTime.now());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        int rows = orderMapper.updateForConfirm(orderToUpdate);
        if (rows == 1) {
            // Step3：订单完成会影响买家/卖家的 completed 统计，因此两边都重算
            creditService.recalcUserCredit(detail.getBuyerId(), CreditReasonType.ORDER_COMPLETED, orderId);
            creditService.recalcUserCredit(detail.getSellerId(), CreditReasonType.ORDER_COMPLETED, orderId);

            // Day13 Step8：订单完成发放积分
            pointsService.grantPointsForOrderComplete(orderId, detail.getBuyerId(), detail.getSellerId());

            safePublish("ORDER_STATUS_CHANGED(confirm)", () ->
                    orderEventProducer.sendOrderStatusChanged(
                            detail.getOrderId(),
                            detail.getOrderNo(),
                            currentStatus.getDbValue(),
                            OrderStatus.COMPLETED.getDbValue(),
                            currentUserId
                    )
            );

            return "确认收货成功";
        }

        // rows==0：重新查询获取最新状态（可能被其他线程修改）
        OrderDetail latestDetail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (latestDetail == null) {
            throw new BusinessException("操作失败，订单可能已被删除或订单关联的用户已被删除");
        }

        OrderStatus s = OrderStatus.fromDbValue(latestDetail.getStatus());
        if (s == null) {
            throw new BusinessException("订单状态异常");
        }

        // 幂等：已确认 -> 直接返回
        if (s == OrderStatus.COMPLETED) {
            return "订单已确认收货，无需重复操作"; // 稍后改为返回提示信息
        }

        if (s == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法确认收货");
        }

        // 其他情况
        throw new BusinessException("确认收货失败，订单状态不允许确认收货：" + latestDetail.getStatus());
    }

    /**
     * 创建或新增相关数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse createOrder(CreateOrderRequest request, Long currentUserId) {

        // 0) 基础参数（一般由 @Validated 做，这里只做关键兜底）
        if (request == null || request.getProductId() == null) {
            throw new BusinessException("商品 ID 不能为空");
        }

        Long productId = request.getProductId();

        // 1) 查商品：拿到卖家 ID与价格（注意要限定未删除；status你可以在这里校验）
        Product product = productMapper.getProductById(productId); // 用你项目里真实的方法名替换
        if (product == null || product.getIsDeleted() == 1) {
            throw new BusinessException("商品不存在");
        }
        if (!ProductStatus.ON_SHELF.getDbValue().equals(product.getStatus())) {
            throw new BusinessException("商品非在售状态，无法下单");
        }

        Long sellerId = product.getOwnerId(); // 你表里是 owner_id
        if (sellerId == null) {
            throw new BusinessException("商品数据异常：缺少卖家信息");
        }

        // 2) 不能买自己的商品（这里才是你想做的“权限/业务校验”）
        if (Objects.equals(sellerId, currentUserId)) {
            throw new BusinessException("不能购买自己发布的商品");
        }

        // 3) 原子占用商品（防重复购买的核心）：抢到=1，抢不到=0
        int rows = orderMapper.markProductSoldIfOnSale(productId);
        if (rows == 0) {
            throw new BusinessException("商品已被购买或不可购买，请刷新后重试");
        }

        // 4) 插入 orders（XML 已 useGeneratedKeys 回填 id）
        Order order = new Order();
        order.setOrderNo(generateOrderNo(currentUserId));
        order.setBuyerId(currentUserId);
        order.setSellerId(sellerId);
        order.setTotalAmount(product.getPrice()); // Day3 固定 quantity=1
        order.setStatus(OrderStatus.PENDING.getDbValue()); // 与你项目的 dbValue 对齐（如有枚举就用枚举）
        order.setShippingAddress(request.getShippingAddress());
        // shippingCompany / trackingNo / shippingRemark 默认 null 即可（你的 insertOrder

        int inserted = orderMapper.insertOrder(order);
        if (inserted != 1 || order.getId() == null) {
            throw new BusinessException("创建订单失败");
        }

        // 5) 插入 order_items 
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(productId);
        item.setPrice(product.getPrice());
        item.setQuantity(1);

        int itemInserted = orderMapper.insertOrderItem(item);
        if (itemInserted != 1) {
            throw new BusinessException("创建订单明细失败");
        }
        // 发送订单创建事件（失败不影响主交易，后续由 Outbox/任务兜底）
        safePublish("ORDER_CREATED", () -> orderEventProducer.sendOrderCreated(order, product));

        // ====== Outbox 入库：ORDER_CREATED ======
        OrderCreatedPayload payload = new OrderCreatedPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setBuyerId(order.getBuyerId());
        payload.setSellerId(order.getSellerId());
        payload.setProductId(product.getId());
        payload.setQuantity(1);
        payload.setPrice(product.getPrice());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setCreateTime(LocalDateTime.now());

        EventMessage<OrderCreatedPayload> message = new EventMessage<>();
        message.setEventId(UUID.randomUUID().toString());
        message.setEventType(OrderEventType.ORDER_CREATED.getCode());
        message.setRoutingKey("order.created");
        message.setBizId(order.getId());
        message.setOccurredAt(LocalDateTime.now());
        message.setPayload(payload);

        MessageOutbox outbox = new MessageOutbox();
        outbox.setEventId(message.getEventId());
        outbox.setEventType(message.getEventType());
        outbox.setRoutingKey(message.getRoutingKey());
        outbox.setExchangeName("order.events.exchange");
        outbox.setBizId(order.getId());
        outbox.setPayloadJson(toJsonSafely(message));
        outbox.setStatus("NEW");
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(null);

        outboxService.save(outbox);


        // 发送订单超时延迟消息（失败不影响主交易，仍有 DB 扫描 Job 兜底）
        safePublish("ORDER_TIMEOUT_DELAY", () -> orderEventProducer.sendOrderTimeoutDelay(order));


        // 6) 返回响应（SQL 用 NOW()，这里返回当前时间即可；若要严格一致可再查一次订单）
        CreateOrderResponse resp = new CreateOrderResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreateTime(LocalDateTime.now());
        return resp;
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String payOrder(Long orderId, Long currentUserId) {
        // 1) 先尝试条件更新：pending -> paid
        int rows = orderMapper.updateForPay(orderId, currentUserId);
        if (rows == 1) {
            //支付成功后创建“48小时违法或超时任务”（幂等）
            createShipTimeoutTaskIfAbsent(orderId,"payorder:update_success");
            return "支付成功";
        }

        // 2) rows==0：再查当前状态做幂等/非法分流
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权操作该订单");
        }

        OrderStatus s = OrderStatus.fromDbValue(detail.getStatus());
        if (s == null) {
            throw new BusinessException("订单状态异常");
        }

         // 幂等：已支付 -> 仍补建超时任务（修复历史漏建场景）
        if (s == OrderStatus.PAID) {
            createShipTimeoutTaskIfAbsent(orderId, "payOrder:idempotent_paid");
            return "订单已支付，无需重复操作";
    }

        // 幂等：已支付/已进入后续状态 -> 直接返回成功提示（不要抛异常）
        if ( s == OrderStatus.SHIPPED || s == OrderStatus.COMPLETED) {
            return "订单已支付，无需重复操作";
        }

        if (s == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法支付");
        }

        // 理论上 pending 时 rows 应该=1，走到这里多半是并发/脏数据/where条件不一致
        throw new BusinessException("支付失败，订单状态不允许支付：" + detail.getStatus());
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelOrder(Long orderId, CancelOrderRequest request, Long currentUserId) {
        String reason = (request == null || request.getReason() == null || request.getReason().trim().isEmpty())
                ? "buyer_cancel"
                : request.getReason().trim();

        // 1) 先尝试“条件更新”：pending -> cancelled
        int rows = orderMapper.updateForCancel(orderId, currentUserId, reason);
        if (rows == 1) {
            orderMapper.releaseProductsForOrder(orderId);
            // Step3：订单取消会影响买家 cancelled 统计，因此重算买家
            creditService.recalcUserCredit(currentUserId, CreditReasonType.ORDER_CANCELLED, orderId);

            return "取消成功";
        }


        // 3) rows==0：判断幂等 or 不允许取消
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权操作该订单");
        }
        if (!Objects.equals(detail.getBuyerId(), currentUserId)) {
            throw new BusinessException("只有买家本人可以取消");
        }

        OrderStatus s = OrderStatus.fromDbValue(detail.getStatus());
        if (s == null) {
            throw new BusinessException("订单状态异常");
        }

        // 幂等口径：已 cancelled -> 当成功返回
        if (s == OrderStatus.CANCELLED) {
            return "订单已取消，无需重复操作";
        }

        // 已支付后不允许取消（后续退款/售后再做）
        if (s == OrderStatus.PAID || s == OrderStatus.SHIPPED || s == OrderStatus.COMPLETED) {
            throw new BusinessException("订单已支付，当前不允许取消（后续走退款/售后）");
        }

        throw new BusinessException("取消失败，订单状态不允许取消：" + detail.getStatus());
    }

    /**
     * 处理对应业务流程。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handlePaymentCallback(com.demo.dto.payment.PaymentCallbackRequest request) {
        // 1) 占位验签（Day13 冻结：sign 非空 + timestamp 在 5 分钟内）
        if (request.getSign() == null || request.getSign().trim().isEmpty()) {
            throw new BusinessException("签名不能为空");
        }
        //时间戳校验
        long now = System.currentTimeMillis() / 1000;
        long diff = Math.abs(now - request.getTimestamp());
        if (diff > 300) { // 5分钟 = 300秒
            throw new BusinessException("回调时间戳超时（超过5分钟）");
        }

        // 2) 只有 status=SUCCESS 才触发订单状态更新
        if (!"SUCCESS".equalsIgnoreCase(request.getStatus())) {
            return "回调状态非成功，不做处理";
        }

        // 3) 根据 orderNo 查询订单
        Order order = orderMapper.selectOrderByOrderNo(request.getOrderNo());
        if (order == null) {
            throw new BusinessException("订单不存在：" + request.getOrderNo());
        }

        // 4) 幂等：若已 paid/shipped/completed，直接返回成功
        OrderStatus s = OrderStatus.fromDbValue(order.getStatus());
        if (s == OrderStatus.PAID){
            // 历史漏建修复：已支付也补建任务
            createShipTimeoutTaskIfAbsent(order.getId(), "paymentCallback:idempotent_paid");
            return "订单已支付，回调幂等成功!";
        }
        if (s == OrderStatus.SHIPPED || s == OrderStatus.COMPLETED) {
            return "订单已支付，回调幂等成功";
        }

        // 5) 若已取消，提示不可支付
        if (s == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法支付");
        }

        // 6) 尝试更新 pending -> paid（条件更新）
        int rows = orderMapper.updateForPayByOrderNo(request.getOrderNo());
        if (rows == 1) {
            safePublish("ORDER_PAID", () -> orderEventProducer.sendOrderPaid(order, request.getAmount(), request.getChannel()));
            // ====== Outbox 入库：ORDER_PAID ======
            EventMessage<com.demo.dto.mq.OrderPaidPayload> paidMsg = new EventMessage<>();
            paidMsg.setEventId(UUID.randomUUID().toString());
            paidMsg.setEventType(OrderEventType.ORDER_PAID.getCode());
            paidMsg.setRoutingKey("order.paid");
            paidMsg.setBizId(order.getId());
            paidMsg.setOccurredAt(LocalDateTime.now());

            com.demo.dto.mq.OrderPaidPayload paidPayload = new com.demo.dto.mq.OrderPaidPayload();
            paidPayload.setOrderId(order.getId());
            paidPayload.setOrderNo(order.getOrderNo());
            paidPayload.setBuyerId(order.getBuyerId());
            paidPayload.setPayAmount(request.getAmount());
            paidPayload.setPayMethod(request.getChannel());
            paidPayload.setPayTime(LocalDateTime.now());

            paidMsg.setPayload(paidPayload);

            MessageOutbox paidOutbox = new MessageOutbox();
            paidOutbox.setEventId(paidMsg.getEventId());
            paidOutbox.setEventType(paidMsg.getEventType());
            paidOutbox.setRoutingKey(paidMsg.getRoutingKey());
            paidOutbox.setExchangeName("order.events.exchange");
            paidOutbox.setBizId(order.getId());
            paidOutbox.setPayloadJson(toJsonSafely(paidMsg));
            paidOutbox.setStatus("NEW");
            paidOutbox.setRetryCount(0);
            paidOutbox.setNextRetryTime(null);

            outboxService.save(paidOutbox);

            //day15:支付成功后创建48h未发货超时任务
            createShipTimeoutTaskIfAbsent(order.getId(), "paymentCallback:update_success");


            return "支付回调处理成功";
        }

        // 7) rows==0：可能并发/重复回调，再次查询做幂等判断
        Order latest = orderMapper.selectOrderByOrderNo(request.getOrderNo());
        if (latest == null) {
            throw new BusinessException("订单不存在（可能已被删除）");
        }
        OrderStatus latestStatus = OrderStatus.fromDbValue(latest.getStatus());
        if (latestStatus == OrderStatus.PAID || latestStatus == OrderStatus.SHIPPED || latestStatus == OrderStatus.COMPLETED) {
            return "订单已支付，回调幂等成功";
        }

        throw new BusinessException("支付回调处理失败，订单状态不允许支付：" + latest.getStatus());
    }

    /**
     * 支付成功后，幂等创建“发货超时任务”。
     * <p>
     * 设计说明：
     * 1) 使用 INSERT IGNORE + uk(order_id) 保证幂等，重复调用不会报错。
     * 2) deadline_time 以“当前时刻 + N小时”计算，和 pay_time 的 NOW() 口径保持一致（秒级误差可接受）。
     * 3) 该方法必须是“弱依赖”：失败时不应该阻塞支付主链路（仅记录日志）。
     *
     * @param orderId 订单 ID
     * @param scene   调用场景（便于日志排查）
     */
    private void createShipTimeoutTaskIfAbsent(Long orderId, String scene) {
        if (orderId == null) {
            log.warn("skip create ship-timeout task because orderId is null, scene={}", scene);
            return;
        }
        // 兜底：防止误配置导致 <=0
        int hours = shipTimeoutHours <= 0 ? 48 : shipTimeoutHours;

        OrderShipTimeoutTask task = new OrderShipTimeoutTask();
        task.setOrderId(orderId);
        task.setDeadlineTime(LocalDateTime.now().plusHours(hours));
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryTime(null);
        task.setLastError(null);

        try {
            int rows = orderShipTimeoutTaskMapper.insertIgnore(task);
            if (rows == 1) {
                log.info("create ship-timeout task success, orderId={}, deadlineTime={}, scene={}",
                        orderId, task.getDeadlineTime(), scene);
            } else {
                // rows=0 代表任务已存在（幂等命中），属于正常情况
                log.info("ship-timeout task already exists, orderId={}, scene={}", orderId, scene);
            }
        } catch (Exception ex) {
            // 不抛异常，避免影响支付成功结果
            log.error("create ship-timeout task failed, orderId={}, scene={}", orderId, scene, ex);
        }
    }


    private String generateOrderNo(Long buyerId) {
        String time = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
        return time + (buyerId % 10000) + rnd;
    }

    /**
     * 安全序列化为 JSON
     * - 失败时抛 BusinessException，保证事务回滚
     */
    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("消息序列化失败，请检查字段是否可序列化");
        }
    }

    /**
     * MQ 发送失败不应阻塞主交易，避免把业务成功误判为“服务器错误”。
     */
    private void safePublish(String scene, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("MQ publish failed, scene={}", scene, ex);
        }
    }

    private void pageValidated(PageQueryDTO pageQueryDTO) {
        Integer page = pageQueryDTO.getPage();
        Integer size = pageQueryDTO.getPageSize();
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        PageHelper.startPage(page, size);
    }
}

