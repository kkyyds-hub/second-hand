package com.demo.service.serviceimpl;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.CancelOrderRequest;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.entity.Product;
import com.demo.enumeration.OrderStatus;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.result.PageResult;
import com.demo.service.OrderService;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    public PageResult<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId) {
        pageValidated(pageQueryDTO);
        List<BuyerOrderSummary> list = orderMapper.listBuyerOrders(currentUserId, pageQueryDTO);
        PageInfo<BuyerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }

    @Override
    public PageResult<SellerOrderSummary> getSellOrder(PageQueryDTO dto, Long uid) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());

        List<SellerOrderSummary> list = orderMapper.listSellerOrders(uid, dto);

        PageInfo<SellerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }


    @Override
    public OrderDetail getOrderDetail(Long orderId , Long currentUserId) {
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }
        return detail;
    }

    @Override
    public void shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId) {
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
        if (currentStatus != OrderStatus.PAID) {
            throw new BusinessException("订单当前状态不允许发货，只能对已付款订单发货");
        }

        // 4. 组装 Order 作为更新入参（只放 orders 表需要的字段）
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setSellerId(currentUserId);                 // 供 SQL where 使用（建议）
        orderToUpdate.setShippingCompany(request.getShippingCompany());
        orderToUpdate.setTrackingNo(request.getTrackingNo());
        orderToUpdate.setShippingRemark(request.getRemark());     // Day5：把备注落库
        orderToUpdate.setStatus(OrderStatus.SHIPPED.getDbValue());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        // 5. 执行更新 + 乐观校验
        int rows = orderMapper.updateForShipping(orderToUpdate);
        if (rows == 0) {
            throw new BusinessException("发货失败，订单状态可能已变更");
        }
    }

    @Override
    public void confirmOrder(Long orderId, Long currentUserId) {
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
        if (currentStatus != OrderStatus.SHIPPED) {
            throw new BusinessException("订单当前状态不允许确认收货，只能对已发货订单确认收货");
        }

        // 4. 组装 Order 做更新
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setBuyerId(currentUserId);                  // 供 SQL where 使用（建议）
        orderToUpdate.setStatus(OrderStatus.COMPLETED.getDbValue());
        orderToUpdate.setCompleteTime(LocalDateTime.now());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        int rows = orderMapper.updateForConfirm(orderToUpdate);
        if (rows == 0) {
            throw new BusinessException("确认收货失败，订单状态可能已变更");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse createOrder(CreateOrderRequest request, Long currentUserId) {

        // 0) 基础参数（一般由 @Validated 做，这里只做关键兜底）
        if (request == null || request.getProductId() == null) {
            throw new BusinessException("商品ID不能为空");
        }

        Long productId = request.getProductId();

        // 1) 查商品：拿到卖家ID与价格（注意要限定未删除；status你可以在这里校验）
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

        // 4) 插入 orders（你的 XML 已 useGeneratedKeys 回填 id） :contentReference[oaicite:1]{index=1}
        Order order = new Order();
        order.setOrderNo(generateOrderNo(currentUserId));
        order.setBuyerId(currentUserId);
        order.setSellerId(sellerId);
        order.setTotalAmount(product.getPrice());     // Day3 固定 quantity=1
        order.setStatus(OrderStatus.PENDING.getDbValue());                // 与你项目的 dbValue 对齐（如有枚举就用枚举）
        order.setShippingAddress(request.getShippingAddress());
        // shippingCompany / trackingNo / shippingRemark 默认 null 即可（你的 insertOrder 会插入这些字段） :contentReference[oaicite:2]{index=2}

        int inserted = orderMapper.insertOrder(order);
        if (inserted != 1 || order.getId() == null) {
            throw new BusinessException("创建订单失败");
        }

        // 5) 插入 order_items :contentReference[oaicite:3]{index=3}
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(productId);
        item.setPrice(product.getPrice());
        item.setQuantity(1);

        int itemInserted = orderMapper.insertOrderItem(item);
        if (itemInserted != 1) {
            throw new BusinessException("创建订单明细失败");
        }

        // 6) 返回响应（时间你 SQL 用 NOW()，这里返回当前时间即可；若要严格一致可再查一次订单）
        CreateOrderResponse resp = new CreateOrderResponse();
        resp.setOrderId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setTotalAmount(order.getTotalAmount());
        resp.setCreateTime(LocalDateTime.now());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String payOrder(Long orderId, Long currentUserId) {
        // 1) 先尝试条件更新：pending -> paid
        int rows = orderMapper.updateForPay(orderId, currentUserId);
        if (rows == 1) {
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

        // 幂等：已支付/已进入后续状态 -> 直接返回成功提示（不要抛异常）
        if (s == OrderStatus.PAID || s == OrderStatus.SHIPPED || s == OrderStatus.COMPLETED) {
            return "订单已支付，无需重复操作";
        }

        if (s == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法支付");
        }

        // 理论上 pending 时 rows 应该=1，走到这里多半是并发/脏数据/where条件不一致
        throw new BusinessException("支付失败，订单状态不允许支付：" + detail.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelOrder(Long orderId, CancelOrderRequest request, Long currentUserId) {
        String reason = (request == null || request.getReason() == null || request.getReason().trim().isEmpty())
                ? "buyer_cancel"
                : request.getReason().trim();

        // 1) 先尝试“条件更新”：pending -> cancelled
        int rows = orderMapper.updateForCancel(orderId, currentUserId, reason);
        if (rows == 1) {
            // 2) 取消成功后释放商品：sold -> on_sale（你现在 sold 充当“锁定”）
            orderMapper.releaseProductsForOrder(orderId);
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


    private String generateOrderNo(Long buyerId) {
        String time = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rnd = java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000);
        return time + (buyerId % 10000) + rnd;
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

