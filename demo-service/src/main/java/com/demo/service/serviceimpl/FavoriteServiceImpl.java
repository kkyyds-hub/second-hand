package com.demo.service.serviceimpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.constant.MessageConstant;
import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.favorite.FavoriteActionResponse;
import com.demo.dto.favorite.FavoriteItemDTO;
import com.demo.entity.Favorite;
import com.demo.entity.Product;
import com.demo.exception.BusinessException;
import com.demo.mapper.FavoriteMapper;
import com.demo.mapper.ProductMapper;
import com.demo.result.PageResult;
import com.demo.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private com.demo.service.port.FavoriteReadPort favoriteReadPort;


    @Override
    @Transactional
    public FavoriteActionResponse favorite(Long userId, Long productId) {
        // 冻结口径：只允许收藏在售商品
        requireOnSaleProduct(productId);

        // 1) 先恢复（只命中 is_deleted=1）
        int restored = baseMapper.restoreDeleted(userId, productId);
        if (restored > 0) {
            return new FavoriteActionResponse(productId, true);
        }

        // 2) 再插入（可能并发 DuplicateKey）
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setProductId(productId);
        fav.setIsDeleted(0);

        try {
            this.save(fav); // MP 写法，等价于 baseMapper.insert(fav)
            return new FavoriteActionResponse(productId, true);
        } catch (DuplicateKeyException e) {
            // 并发下别人已经插入成功：幂等语义 => 当成功
            return new FavoriteActionResponse(productId, true);
        }
    }

    @Override
    @Transactional
    public FavoriteActionResponse unfavorite(Long userId, Long productId) {
        // 幂等语义：未收藏取消也应返回成功
        baseMapper.softDelete(userId, productId);
        return new FavoriteActionResponse(productId, false);
    }

    @Override
    public boolean isFavorited(Long userId, Long productId) {
        return favoriteReadPort.isFavorited(userId, productId);
    }

    @Override
    public PageResult<FavoriteItemDTO> pageMyFavorites(Long userId, PageQueryDTO q) {

        Page<Favorite> page = new Page<>(q.getPage(), q.getPageSize());

        Page<Favorite> favPage = this.page(page, new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getIsDeleted, 0)
                .orderByDesc(Favorite::getCreateTime));

        List<Favorite> records = favPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageResult<>(
                    Collections.emptyList(),
                    0L,
                    q.getPage(),
                    q.getPageSize()
            );
        }

        // 1) 收集 productIds
        List<Long> productIds = records.stream()
                .map(Favorite::getProductId)
                .distinct()
                .collect(Collectors.toList());

        // 2) 批量查商品，并转 Map 方便回填
        final Map<Long, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.listByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 3) 组装 DTO（回填 title/price/coverUrl/status）
        List<FavoriteItemDTO> list = records.stream()
                .map(f -> toItemDTO(f, productMap))
                .collect(Collectors.toList());

        return new PageResult<>(
                list,
                favPage.getTotal(),
                (int) favPage.getCurrent(),
                (int) favPage.getSize()
        );
    }

    // -------------------- Step5：抽方法收口 --------------------

    private Product requireOnSaleProduct(Long productId) {
        Product p = productMapper.getProductById(productId);
        if (p == null) {
            throw new BusinessException(MessageConstant.PRODUCT_NOT_FOUND);
        }
        if (!"on_sale".equals(p.getStatus())) {
            throw new BusinessException(MessageConstant.FAVORITE_ONLY_ON_SALE);
        }
        return p;
    }

    private FavoriteItemDTO toItemDTO(Favorite f, Map<Long, Product> productMap) {
        FavoriteItemDTO dto = new FavoriteItemDTO();
        dto.setProductId(f.getProductId());
        dto.setFavoritedAt(f.getCreateTime());

        Product p = productMap.get(f.getProductId());
        if (p != null) {
            dto.setTitle(p.getTitle());
            dto.setPrice(p.getPrice());   // 确保 FavoriteItemDTO.price 与 Product.price 类型一致（建议 BigDecimal）
            dto.setStatus(p.getStatus());
            dto.setCoverUrl(firstImage(p.getImages()));
        }
        return dto;
    }

    private String firstImage(String images) {
        if (images == null) return null;
        String s = images.trim();
        if (s.isEmpty()) return null;
        String[] arr = s.split(",");
        return arr.length == 0 ? null : arr[0].trim();
    }
}
