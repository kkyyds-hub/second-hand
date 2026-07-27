package com.demo.service.serviceimpl;

import com.demo.context.BaseContext;
import com.demo.dto.user.SellerShopProductDTO;
import com.demo.dto.user.SellerShopProductQueryDTO;
import com.demo.dto.user.SellerShopProfileDTO;
import com.demo.dto.user.SellerShopStatsDTO;
import com.demo.entity.Product;
import com.demo.entity.User;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.SellerShopService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 卖家小店公开页面服务实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SellerShopServiceImpl implements SellerShopService {

    private static final String SHOP_NAME_SUFFIX = "的小店";
    private static final Set<String> ALLOWED_PRODUCT_STATUSES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("on_sale", "sold")));

    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(readOnly = true)
    public SellerShopProfileDTO getShopProfile(Long sellerId) {
        Long currentUserId = BaseContext.getCurrentId();

        // 1) 先查含已删除用户，区分"不存在"与"已删除"
        User userIncludeDeleted = userMapper.selectByIdIncludeDeleted(sellerId);
        if (userIncludeDeleted == null) {
            throw new BusinessException("卖家不存在");
        }

        // 2) 正常查询（过滤 is_deleted=0）
        User user = userMapper.selectById(sellerId);
        if (user == null) {
            throw new BusinessException("该小店已不存在");
        }

        // 3) 封禁或禁用用户不可访问
        if (isBannedOrDisabled(user)) {
            throw new BusinessException("该小店暂不可访问");
        }

        // 4) 非卖家用户
        if (!isSeller(user)) {
            throw new BusinessException("该用户尚未开通小店");
        }

        // 5) 构造概览
        SellerShopProfileDTO dto = new SellerShopProfileDTO();
        dto.setSellerId(user.getId());

        String nickname = normalizeNickname(user);
        dto.setNickname(nickname);
        dto.setShopName(nickname + SHOP_NAME_SUFFIX);
        dto.setAvatarUrl(user.getAvatar());
        dto.setBio(normalizeBio(user.getBio()));
        dto.setCreditScore(user.getCreditScore());
        dto.setRegisteredAt(user.getCreateTime());
        dto.setIsCurrentUser(currentUserId != null && currentUserId.equals(user.getId()));

        // 6) 统计聚合
        SellerShopStatsDTO stats = productMapper.getSellerShopStats(sellerId);
        if (stats != null) {
            dto.setOnSaleCount(nz(stats.getOnSaleCount()));
            dto.setSoldCount(nz(stats.getSoldCount()));
        } else {
            dto.setOnSaleCount(0L);
            dto.setSoldCount(0L);
        }

        Long completedOrders = orderMapper.countCompletedBySellerId(sellerId);
        dto.setCompletedOrderCount(nz(completedOrders));

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SellerShopProductDTO> getShopProducts(Long sellerId, SellerShopProductQueryDTO queryDTO) {
        // 1) 先查含已删除用户，区分"不存在"与"已删除"
        User userIncludeDeleted = userMapper.selectByIdIncludeDeleted(sellerId);
        if (userIncludeDeleted == null) {
            throw new BusinessException("卖家不存在");
        }

        // 2) 正常查询（过滤 is_deleted=0）
        User user = userMapper.selectById(sellerId);
        if (user == null) {
            throw new BusinessException("该小店已不存在");
        }
        if (isBannedOrDisabled(user)) {
            throw new BusinessException("该小店暂不可访问");
        }
        if (!isSeller(user)) {
            throw new BusinessException("该用户尚未开通小店");
        }

        // 2) 状态白名单校验
        String status = normalizeStatus(queryDTO.getStatus());
        if (!ALLOWED_PRODUCT_STATUSES.contains(status)) {
            throw new BusinessException("商品状态仅支持 on_sale 或 sold");
        }

        // 3) 分页查询
        int page = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        int pageSize = queryDTO.getPageSize() != null ? Math.min(queryDTO.getPageSize(), 24) : 12;
        Long excludeProductId = queryDTO.getExcludeProductId();

        PageHelper.startPage(page, pageSize);
        List<Product> products = productMapper.getSellerShopProducts(sellerId, status, excludeProductId);
        PageInfo<Product> pageInfo = new PageInfo<>(products);

        List<SellerShopProductDTO> dtoList = products.stream()
                .map(this::toShopProductDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, pageInfo.getTotal(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    // ================== 私有辅助方法 ==================

    private SellerShopProductDTO toShopProductDTO(Product product) {
        SellerShopProductDTO dto = new SellerShopProductDTO();
        dto.setProductId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setCoverUrl(extractFirstImage(product.getImages()));
        dto.setPrice(product.getPrice());
        dto.setCategoryName(product.getCategory());
        dto.setStatus(product.getStatus());
        dto.setCreateTime(product.getCreateTime());
        return dto;
    }

    private String extractFirstImage(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        for (String s : images.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private String normalizeNickname(User user) {
        if (user.getNickname() != null && !user.getNickname().trim().isEmpty()) {
            return user.getNickname().trim();
        }
        return "卖家 #" + user.getId();
    }

    private String normalizeBio(String bio) {
        if (bio == null || bio.trim().isEmpty()) {
            return null;
        }
        return bio.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "on_sale";
        }
        return status.trim();
    }

    private boolean isBannedOrDisabled(User user) {
        String status = user.getStatus();
        return "banned".equals(status) || "disabled".equals(status);
    }

    private boolean isSeller(User user) {
        return Integer.valueOf(1).equals(user.getIsSeller());
    }

    private Long nz(Long value) {
        return value == null ? 0L : value;
    }
}
