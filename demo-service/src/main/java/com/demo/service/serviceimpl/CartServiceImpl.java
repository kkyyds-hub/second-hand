package com.demo.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.dto.cart.AddCartItemRequest;
import com.demo.dto.cart.BatchDeleteCartItemsRequest;
import com.demo.dto.cart.CartCheckoutRequest;
import com.demo.dto.cart.CartItemRow;
import com.demo.dto.user.CreateOrderRequest;
import com.demo.dto.user.CreateOrderResponse;
import com.demo.entity.CartItem;
import com.demo.entity.Product;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.CartMapper;
import com.demo.mapper.ProductMapper;
import com.demo.service.AddressService;
import com.demo.service.CartService;
import com.demo.service.OrderService;
import com.demo.vo.cart.CartCheckoutFailureVO;
import com.demo.vo.cart.CartCheckoutResponse;
import com.demo.vo.cart.CartCheckoutSuccessVO;
import com.demo.vo.cart.CartCountVO;
import com.demo.vo.cart.CartItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 购物车服务实现。
 *
 * 设计要点：
 * 1) 加入购物车不占用商品、不写订单、不写 Outbox；
 * 2) 批量结算逐项通过 OrderService Spring 代理调用 createOrder，
 *    每项保持独立事务语义，部分成功不回滚其他成功订单；
 * 3) 列表/数量动态识别商品失效，不自动删除失效项。
 */
@Service
@Slf4j
public class CartServiceImpl extends ServiceImpl<CartMapper, CartItem> implements CartService {

    /** 购物车总量上限。 */
    private static final int CART_MAX_SIZE = 50;

    /** 单次批量结算去重后上限。 */
    private static final int CHECKOUT_MAX_SIZE = 20;

    /** 商品被软删除时对外展示的状态值。 */
    private static final String STATUS_DELETED = "deleted";

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private AddressService addressService;

    /**
     * 订单服务。通过 Spring 代理调用 createOrder，保证每项订单独立事务。
     */
    @Autowired
    private OrderService orderService;

    @Override
    public List<CartItemVO> listItems(Long userId) {
        List<CartItemRow> rows = baseMapper.listCartItems(userId);
        List<CartItemVO> result = new ArrayList<>(rows.size());
        for (CartItemRow row : rows) {
            result.add(toItemVO(row, userId));
        }
        return result;
    }

    @Override
    public CartCountVO count(Long userId) {
        List<CartItemRow> rows = baseMapper.listCartItems(userId);
        int total = rows.size();
        int available = 0;
        for (CartItemRow row : rows) {
            if (isAvailable(row, userId)) {
                available++;
            }
        }
        CartCountVO vo = new CartCountVO();
        vo.setTotal(total);
        vo.setAvailable(available);
        return vo;
    }

    @Override
    public CartItemVO add(Long userId, AddCartItemRequest request) {
        Long productId = request.getProductId();

        // 1) 商品校验：存在、未删除、在售、非本人商品。
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!ProductStatus.ON_SHELF.getDbValue().equals(product.getStatus())) {
            throw new BusinessException("仅在售商品可以加入购物车");
        }
        if (Objects.equals(product.getOwnerId(), userId)) {
            throw new BusinessException("不能购买自己发布的商品");
        }

        // 2) 幂等：已存在则直接返回已有购物车项。
        CartItem existing = getOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("购物车项已存在，幂等返回：userId={}, productId={}, cartItemId={}",
                    userId, productId, existing.getId());
            return buildItemVOFromProduct(existing.getId(), existing.getCreateTime(), product, userId);
        }

        // 3) 总量上限校验（仅对新增生效）。
        long currentCount = count(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        if (currentCount >= CART_MAX_SIZE) {
            throw new BusinessException("购物车已达上限（最多 " + CART_MAX_SIZE + " 项）");
        }

        // 4) 插入；唯一索引作为最终并发保护。
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setProductId(productId);
        try {
            save(item);
        } catch (DuplicateKeyException e) {
            // 并发下已插入：幂等当成功，回查已有行。
            CartItem concurrent = getOne(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getUserId, userId)
                    .eq(CartItem::getProductId, productId)
                    .last("LIMIT 1"));
            if (concurrent != null) {
                log.info("并发加入命中唯一索引，幂等返回：userId={}, productId={}, cartItemId={}",
                        userId, productId, concurrent.getId());
                return buildItemVOFromProduct(concurrent.getId(), concurrent.getCreateTime(), product, userId);
            }
            return buildItemVOFromProduct(null, null, product, userId);
        }

        log.info("加入购物车成功：userId={}, productId={}, cartItemId={}", userId, productId, item.getId());
        return buildItemVOFromProduct(item.getId(), item.getCreateTime(), product, userId);
    }

    @Override
    public void delete(Long userId, Long cartItemId) {
        // 仅删除当前用户自己的购物车项；他人 ID 表现为不存在（删除 0 行），不泄漏归属。
        int rows = baseMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId)
                .eq(CartItem::getUserId, userId));
        if (rows > 0) {
            log.info("删除购物车项成功：userId={}, cartItemId={}", userId, cartItemId);
        } else {
            // 幂等：重复删除或无权限均静默成功。
            log.info("删除购物车项幂等命中：userId={}, cartItemId={}, rows=0", userId, cartItemId);
        }
    }

    @Override
    public int batchDelete(Long userId, BatchDeleteCartItemsRequest request) {
        // 去重（保持无意义，删除按集合即可）。
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : request.getCartItemIds()) {
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        int rows = baseMapper.delete(new LambdaQueryWrapper<CartItem>()
                .in(CartItem::getId, ids)
                .eq(CartItem::getUserId, userId));
        log.info("批量删除购物车项：userId={}, requested={}, deleted={}", userId, ids.size(), rows);
        return rows;
    }

    @Override
    public CartCheckoutResponse checkout(Long userId, CartCheckoutRequest request) {
        // 1) 地址整体校验：必须存在、属于当前用户、字段完整。
        //    getAddressById 在非法时抛 BusinessException，从而在创建任何订单前整体失败。
        addressService.getAddressById(userId, request.getAddressId());

        // 2) 去重并保持请求原始顺序。
        List<Long> orderedIds = new ArrayList<>(new LinkedHashSet<>(request.getCartItemIds()));
        orderedIds.removeIf(id -> id == null || id <= 0);
        if (orderedIds.isEmpty()) {
            throw new BusinessException("购物车项 ID 列表不能为空");
        }
        if (orderedIds.size() > CHECKOUT_MAX_SIZE) {
            throw new BusinessException("单次最多结算 " + CHECKOUT_MAX_SIZE + " 项");
        }

        // 3) 查询当前用户选中的购物车项，建立 id -> 行 映射（仅本人）。
        List<CartItem> selected = list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, orderedIds));
        Map<Long, CartItem> itemMap = new LinkedHashMap<>();
        for (CartItem ci : selected) {
            itemMap.put(ci.getId(), ci);
        }

        CartCheckoutResponse response = new CartCheckoutResponse();
        response.setRequestedCount(orderedIds.size());

        // 4) 按去重后的原始顺序逐项结算。
        for (Long cartItemId : orderedIds) {
            CartItem cartItem = itemMap.get(cartItemId);
            if (cartItem == null) {
                // 不属于当前用户或已不存在：记为失败，不泄漏归属。
                response.getFailures().add(buildFailure(cartItemId, null, "购物车项不存在或无权操作"));
                continue;
            }
            Long productId = cartItem.getProductId();
            try {
                CreateOrderRequest orderRequest = new CreateOrderRequest();
                orderRequest.setProductId(productId);
                orderRequest.setAddressId(request.getAddressId());

                CreateOrderResponse orderResponse = orderService.createOrder(orderRequest, userId);

                CartCheckoutSuccessVO successVO = new CartCheckoutSuccessVO();
                successVO.setCartItemId(cartItemId);
                successVO.setProductId(productId);
                successVO.setOrderId(orderResponse.getOrderId());
                successVO.setOrderNo(orderResponse.getOrderNo());
                successVO.setStatus(orderResponse.getStatus());
                successVO.setTotalAmount(orderResponse.getTotalAmount());
                response.getOrders().add(successVO);

                // 5) 成功项移出购物车（订单事务已独立提交，删除失败不回滚订单）。
                removeCartItemBestEffort(userId, cartItemId, productId);
            } catch (BusinessException ex) {
                // 6) 可识别业务失败：记入 failures，商品保留在购物车。
                log.info("批量结算单项失败：userId={}, cartItemId={}, productId={}, reason={}",
                        userId, cartItemId, productId, ex.getMessage());
                response.getFailures().add(buildFailure(cartItemId, productId, ex.getMessage()));
            } catch (RuntimeException ex) {
                // 7) 未知系统异常：不伪装成商品失效，记录安全日志并中止剩余处理。
                log.error("批量结算遇到未知系统异常，中止剩余处理：userId={}, cartItemId={}, productId={}",
                        userId, cartItemId, productId, ex);
                response.getFailures().add(buildFailure(cartItemId, productId, "系统异常，结算已中止，请稍后重试"));
                break;
            }
        }

        response.setSuccessCount(response.getOrders().size());
        response.setFailureCount(response.getFailures().size());
        log.info("批量结算完成：userId={}, requested={}, success={}, failure={}",
                userId, response.getRequestedCount(), response.getSuccessCount(), response.getFailureCount());
        return response;
    }

    // -------------------- 私有辅助 --------------------

    /**
     * 成功订单创建后移出购物车。
     * 订单已通过 OrderService 独立事务提交，这里删除失败仅记录日志，
     * 不让购物车删除失败影响已创建的订单。
     */
    private void removeCartItemBestEffort(Long userId, Long cartItemId, Long productId) {
        try {
            int rows = baseMapper.delete(new LambdaQueryWrapper<CartItem>()
                    .eq(CartItem::getId, cartItemId)
                    .eq(CartItem::getUserId, userId));
            if (rows == 0) {
                log.warn("结算成功后购物车项删除为 0 行：userId={}, cartItemId={}, productId={}",
                        userId, cartItemId, productId);
            }
        } catch (RuntimeException ex) {
            log.error("结算成功后删除购物车项失败（订单已创建）：userId={}, cartItemId={}, productId={}",
                    userId, cartItemId, productId, ex);
        }
    }

    private CartCheckoutFailureVO buildFailure(Long cartItemId, Long productId, String reason) {
        CartCheckoutFailureVO vo = new CartCheckoutFailureVO();
        vo.setCartItemId(cartItemId);
        vo.setProductId(productId);
        vo.setReason(reason);
        return vo;
    }

    /**
     * 将联表原始行转换为对外 VO，并计算可用性与失效原因。
     */
    private CartItemVO toItemVO(CartItemRow row, Long userId) {
        CartItemVO vo = new CartItemVO();
        vo.setCartItemId(row.getCartItemId());
        vo.setProductId(row.getProductId());
        vo.setTitle(row.getTitle());
        vo.setCoverUrl(firstImage(row.getImages()));
        vo.setPrice(row.getPrice());
        vo.setSellerId(row.getSellerId());
        vo.setSellerNickname(row.getSellerNickname());
        vo.setCreateTime(row.getCreateTime());

        String displayStatus = resolveDisplayStatus(row);
        vo.setProductStatus(displayStatus);

        String reason = unavailableReason(row, userId);
        vo.setAvailable(reason == null);
        vo.setUnavailableReason(reason);
        return vo;
    }

    /**
     * 基于商品实体构造购物车项 VO（加入/幂等返回时使用）。
     */
    private CartItemVO buildItemVOFromProduct(Long cartItemId, java.time.LocalDateTime createTime,
                                              Product product, Long userId) {
        CartItemVO vo = new CartItemVO();
        vo.setCartItemId(cartItemId);
        vo.setProductId(product.getId());
        vo.setTitle(product.getTitle());
        vo.setCoverUrl(firstImage(product.getImages()));
        vo.setPrice(product.getPrice());
        vo.setSellerId(product.getOwnerId());
        vo.setSellerNickname(null);
        vo.setProductStatus(product.getStatus());
        vo.setCreateTime(createTime);
        vo.setAvailable(true);
        vo.setUnavailableReason(null);
        return vo;
    }

    /**
     * 计算对外展示的商品状态：商品被软删除或行缺失时统一为 deleted。
     */
    private String resolveDisplayStatus(CartItemRow row) {
        if (row.getProductDeleted() == null || row.getProductDeleted() == 1) {
            return STATUS_DELETED;
        }
        return row.getProductStatus();
    }

    /**
     * 计算不可用原因；返回 null 表示可用。
     */
    private String unavailableReason(CartItemRow row, Long userId) {
        // 商品行缺失或被软删除。
        if (row.getProductDeleted() == null || row.getProductDeleted() == 1) {
            return "商品已删除";
        }
        String status = row.getProductStatus();
        if (status == null) {
            return "商品状态异常";
        }
        switch (status) {
            case "on_sale":
                break;
            case "sold":
                return "商品已售出";
            case "off_shelf":
                return "商品已下架";
            case "under_review":
                return "商品审核中，暂不可购买";
            default:
                return "商品状态异常";
        }
        // 不能购买自己发布的商品。
        if (row.getSellerId() != null && Objects.equals(row.getSellerId(), userId)) {
            return "不能购买自己发布的商品";
        }
        return null;
    }

    private boolean isAvailable(CartItemRow row, Long userId) {
        return unavailableReason(row, userId) == null;
    }

    private String firstImage(String images) {
        if (images == null) {
            return null;
        }
        String s = images.trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] arr = s.split(",");
        return arr.length == 0 ? null : arr[0].trim();
    }
}
