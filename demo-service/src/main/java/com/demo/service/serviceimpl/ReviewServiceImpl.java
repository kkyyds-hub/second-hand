package com.demo.service.serviceimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.constant.MessageConstant;
import com.demo.constant.ReviewConstants;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.review.ReviewCreateRequest;
import com.demo.dto.review.ReviewItemDTO;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.entity.Product;
import com.demo.entity.Review;
import com.demo.entity.User;
import com.demo.enumeration.ReviewRole;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.ReviewMapper;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
/**
 * ReviewServiceImpl 业务组件。
 */
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements ReviewService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    /**
     * 创建评价（买家对卖家）
     */
    @Override
    public Long createReview(Long currentUserId, ReviewCreateRequest request) {
        Long orderId = request.getOrderId();
        String content = request.getContent();
        if (content != null) {
            int len = content.trim().length();
            if (len < 10) {
                throw new BusinessException(MessageConstant.REVIEW_CONTENT_TOO_SHORT);
            }
            if (len > 500) {
                throw new BusinessException(MessageConstant.REVIEW_CONTENT_TOO_LONG);
            }
        }
        // 1. 订单必须存在
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            throw new BusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 2. 当前用户必须是该订单的 buyer
        if (!currentUserId.equals(order.getBuyerId())) {
            throw new BusinessException(MessageConstant.REVIEW_NO_PERMISSION);
        }

        // 3. 订单状态必须为 completed
        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException(MessageConstant.REVIEW_ORDER_NOT_COMPLETED);
        }

        // 4. 应用层幂等：查询该订单是否已评价
        Integer roleCode = ReviewRole.BUYER_TO_SELLER.getCode();
        Review existingReview = this.lambdaQuery()
                .eq(Review::getOrderId, orderId)
                .eq(Review::getRole, roleCode)
                .eq(Review::getIsDeleted, 0)
                .last("LIMIT 1")
                .one();
        if (existingReview != null) {
            throw new BusinessException(MessageConstant.REVIEW_ALREADY_EXISTS);
        }

        // Day13 Step4 - 防刷：同一买家 24 小时内最多 20 条评价
        java.time.LocalDateTime twentyFourHoursAgo = java.time.LocalDateTime.now().minusHours(24);
        Long recentCount = this.lambdaQuery()
                .eq(Review::getBuyerId, currentUserId)
                .eq(Review::getIsDeleted, 0)
                .ge(Review::getCreateTime, twentyFourHoursAgo)
                .count();
        if (recentCount >= 20) {
            throw new BusinessException(MessageConstant.REVIEW_TOO_FREQUENT);
        }

        // 5. 获取 product_id 和 seller_id（从订单）
        Long productId = getProductIdFromOrder(orderId);
        Long sellerId = order.getSellerId();

        // 6. 组装 Review 实体
        Review review = new Review();
        review.setOrderId(orderId);
        review.setProductId(productId);
        review.setBuyerId(currentUserId);
        review.setSellerId(sellerId);
        review.setRole(roleCode);
        review.setRating(request.getRating());
        review.setContent(content);
        review.setIsAnonymous(request.getIsAnonymous() ? ReviewConstants.ANON_YES : ReviewConstants.ANON_NO);
        review.setIsDeleted(0);

        // 7. 插入（捕获唯一键冲突）
        try {
            this.save(review);
        } catch (DuplicateKeyException e) {
            // 数据库层幂等：唯一键冲突转业务异常
            log.warn("唯一键冲突（并发创建评价）：orderId={}, role={}", orderId, roleCode, e);
            throw new BusinessException(MessageConstant.REVIEW_ALREADY_EXISTS);
        }

        log.info("评价创建成功：reviewId={}, orderId={}, buyerId={}", review.getId(), orderId, currentUserId);
        return review.getId();
    }

    /**
     * 我发出的评价（分页）
     */
    @Override
    public PageResult<ReviewItemDTO> listMyReviews(Long currentUserId, PageQueryDTO query) {
        int safePage = (query.getPage() == null || query.getPage() <= 0) ? 1 : query.getPage();
        int safePageSize = (query.getPageSize() == null || query.getPageSize() <= 0) ? 10 : query.getPageSize();

        Page<Review> page = new Page<>(safePage, safePageSize);
        Page<Review> reviewPage = this.page(page, new LambdaQueryWrapper<Review>()
                .eq(Review::getBuyerId, currentUserId)
                .eq(Review::getIsDeleted, 0)
                .orderByDesc(Review::getCreateTime));

        List<Review> reviews = reviewPage.getRecords();
        if (reviews == null || reviews.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), reviewPage.getTotal(), (int) reviewPage.getCurrent(), (int) reviewPage.getSize());
        }

        Map<Long, Product> productMap = loadProductsByIds(reviews);
        Map<Long, User> buyerMap = loadBuyersByIds(reviews);

        // 转换为 DTO
        List<ReviewItemDTO> dtoList = reviews.stream()
                .map(r -> toReviewItemDTO(r, buyerMap, productMap))
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, reviewPage.getTotal(), (int) reviewPage.getCurrent(), (int) reviewPage.getSize());

    }

    /**
     * 商品评价列表（分页）
     */
    @Override
    public PageResult<ReviewItemDTO> listProductReviews(Long productId, PageQueryDTO query) {
        int safePage = (query.getPage() == null || query.getPage() <= 0) ? 1 : query.getPage();
        int safePageSize = (query.getPageSize() == null || query.getPageSize() <= 0) ? 10 : query.getPageSize();

        Page<Review> page = new Page<>(safePage, safePageSize);
        Page<Review> reviewPage = this.page(page, new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .eq(Review::getIsDeleted, 0)
                .orderByDesc(Review::getCreateTime));

        List<Review> reviews = reviewPage.getRecords();
        if (reviews == null || reviews.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), reviewPage.getTotal(), (int) reviewPage.getCurrent(), (int) reviewPage.getSize());
        }

        Map<Long, User> buyerMap = loadBuyersByIds(reviews);
        Map<Long, Product> productMap = new HashMap<>(1);
        Product product = productMapper.getProductById(productId);
        if (product != null) {
            productMap.put(productId, product);
        }

        // 转换为 DTO
        List<ReviewItemDTO> dtoList = reviews.stream()
                .map(r -> toReviewItemDTO(r, buyerMap, productMap))
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, reviewPage.getTotal(), (int) reviewPage.getCurrent(), (int) reviewPage.getSize());
    }

    // ========== 私有辅助方法 ==========
/**
 * 从订单获取 product_id（从 order_items 取第一条）
 */
private Long getProductIdFromOrder(Long orderId) {
    List<OrderItem> items = orderMapper.selectItemsByOrderId(orderId);
    if (items == null || items.isEmpty()) {
        throw new BusinessException("订单商品明细不存在");
    }
    return items.get(0).getProductId();
}

    /**
     * 转换 Review -> ReviewItemDTO（含匿名展示逻辑）
     */
    private ReviewItemDTO toReviewItemDTO(Review review, Map<Long, User> buyerMap, Map<Long, Product> productMap) {
        ReviewItemDTO dto = new ReviewItemDTO();
        dto.setId(review.getId());
        dto.setOrderId(review.getOrderId());
        dto.setProductId(review.getProductId());
        dto.setRating(review.getRating());
        dto.setContent(review.getContent());
        dto.setIsAnonymous(review.getIsAnonymous() == ReviewConstants.ANON_YES);
        dto.setCreateTime(review.getCreateTime());

        // 匿名展示规则（文档 §2.2）
        if (review.getIsAnonymous() == ReviewConstants.ANON_YES) {
            dto.setBuyerDisplayName(ReviewConstants.ANON_DISPLAY_NAME);
            dto.setBuyerAvatar(ReviewConstants.ANON_AVATAR);
        } else {
            User buyer = buyerMap.get(review.getBuyerId());
            if (buyer != null) {
                dto.setBuyerDisplayName(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
                dto.setBuyerAvatar(buyer.getAvatar() != null ? buyer.getAvatar() : "");
            } else {
                dto.setBuyerDisplayName("用户已注销");
                dto.setBuyerAvatar("");
            }
        }

        Product product = productMap.get(review.getProductId());
        if (product != null) {
            dto.setProductTitle(product.getTitle());
            // 从 images 取第一张作为 cover（假设 images 格式为逗号分隔）
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                String[] imgs = product.getImages().split(",");
                dto.setProductCover(imgs[0]);
            }
        }

        return dto;
    }

    private Map<Long, Product> loadProductsByIds(List<Review> reviews) {
        Set<Long> productIds = reviews.stream()
                .map(Review::getProductId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.listByIds(List.copyOf(productIds)).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
    }

    private Map<Long, User> loadBuyersByIds(List<Review> reviews) {
        Set<Long> buyerIds = reviews.stream()
                .filter(review -> review.getIsAnonymous() == null || !review.getIsAnonymous().equals(ReviewConstants.ANON_YES))
                .map(Review::getBuyerId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (buyerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectByIds(List.copyOf(buyerIds)).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }
}
