package com.demo.service.serviceimpl;

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
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewMapper reviewMapper;

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
        Review existingReview = reviewMapper.selectByOrderIdAndRole(orderId, roleCode);
        if (existingReview != null) {
            throw new BusinessException(MessageConstant.REVIEW_ALREADY_EXISTS);
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
        review.setContent(request.getContent());
        review.setIsAnonymous(request.getIsAnonymous() ? ReviewConstants.ANON_YES : ReviewConstants.ANON_NO);

        // 7. 插入（捕获唯一键冲突）
        try {
            reviewMapper.insertReview(review);
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
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<Review> reviews = reviewMapper.listByBuyerId(currentUserId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);

        // 转换为 DTO
        List<ReviewItemDTO> dtoList = reviews.stream()
                .map(r -> convertToDTO(r, currentUserId))
                .collect(Collectors.toList());

                return new PageResult<>(dtoList, pageInfo.getTotal(), query.getPage(), query.getPageSize());
            
    }

    /**
     * 商品评价列表（分页）
     */
    @Override
    public PageResult<ReviewItemDTO> listProductReviews(Long productId, PageQueryDTO query) {
        PageHelper.startPage(query.getPage(), query.getPageSize());
        List<Review> reviews = reviewMapper.listByProductId(productId);
        PageInfo<Review> pageInfo = new PageInfo<>(reviews);

        // 转换为 DTO
        List<ReviewItemDTO> dtoList = reviews.stream()
                .map(r -> convertToDTO(r, null)) // 商品评价列表不需要传 currentUserId
                .collect(Collectors.toList());

                return new PageResult<>(dtoList, pageInfo.getTotal(), query.getPage(), query.getPageSize());
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
    private ReviewItemDTO convertToDTO(Review review, Long currentUserId) {
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
            // 查询买家信息
            User buyer = userMapper.selectById(review.getBuyerId());
            if (buyer != null) {
                dto.setBuyerDisplayName(buyer.getNickname() != null ? buyer.getNickname() : buyer.getUsername());
                dto.setBuyerAvatar(buyer.getAvatar() != null ? buyer.getAvatar() : "");
            } else {
                dto.setBuyerDisplayName("用户已注销");
                dto.setBuyerAvatar("");
            }
        }

        // 可选：填充商品展示字段
        Product product = productMapper.getProductById(review.getProductId());
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
}