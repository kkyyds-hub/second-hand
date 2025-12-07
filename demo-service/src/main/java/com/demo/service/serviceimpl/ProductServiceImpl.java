package com.demo.service.serviceimpl;

import com.demo.context.BaseContext;
import com.demo.dto.user.ProductDTO;
import com.demo.dto.user.ProductDetailDTO;
import com.demo.dto.user.ProductUpdateRequest;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.exception.ProductNotFoundException;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.ProductViolationMapper;
import com.demo.service.ProductService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductViolationMapper productViolationMapper;
    /**
     * 审核列表：分页查询待审核 / 已审核商品
     */
    @Override
    public PageInfo<ProductDTO> getPendingApprovalProducts(int page,
                                                           int size,
                                                           String productName,
                                                           String category,
                                                           String status) {
        PageHelper.startPage(page, size);
        List<Product> productList =
                productMapper.getPendingApprovalProducts(productName, category, status);

        List<ProductDTO> dtoList = productList.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());

        return new PageInfo<>(dtoList);
    }

    /**
     * 管理员审核商品
     * isApproved = true  -> 审核通过，商品上架（ON_SHELF）
     * isApproved = false -> 审核拒绝，商品下架（OFF_SHELF），记录原因
     */
    @Override
    public void approveProduct(Long productId, boolean isApproved, String reason) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }

        if (isApproved) {
            product.setStatus(ProductStatus.ON_SHELF.getDbValue());
            product.setReason(null);
        } else {
            product.setStatus(ProductStatus.OFF_SHELF.getDbValue());
            product.setReason(reason);
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        log.info("商品审核完成，商品ID: {}, 审核结果: {}, 原因: {}",
                productId,
                isApproved ? "通过" : "未通过",
                reason);
    }

    /**
     * 查询商品的违规记录
     */
    @Override
    public List<ProductViolation> getProductViolations(Long productId) {
        return productViolationMapper.findByProductId(productId);
    }

    /**
     * 添加商品违规记录
     */
    @Override
    public void addProductViolation(ProductViolation violation) {
        productViolationMapper.insert(violation);
    }

    /**
     * 直接更新商品状态（慎用：建议只在内部封装好场景再调用）
     */
    @Override
    public void updateProductStatus(Long productId, String statusDbValue) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }

        // 使用枚举做一层校验，防止乱写
        ProductStatus newStatus = ProductStatus.fromDbValue(statusDbValue);
        product.setStatus(newStatus.getDbValue());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        log.info("商品状态更新成功，商品ID: {}, 新状态: {}", productId, newStatus);
    }

    /**
     * 查询用户自己的商品列表
     */
    @Override
    public PageInfo<Product> getUserProducts(UserProductQueryDTO queryDTO) {
        // 这里你也可以直接用 BaseContext.getCurrentId()，避免前端传 userId
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getSize());
        List<Product> products =
                productMapper.getUserProducts(queryDTO.getUserId(), queryDTO.getStatus());
        return new PageInfo<>(products);
    }

    /**
     * 查询当前登录用户的某个商品详情
     */
    @Override
    public ProductDetailDTO getProductDetail(Long productId) {
        Long currentUserId = BaseContext.getCurrentId();

        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }

        // 权限校验：只能看自己发布的商品
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException("无权查看该商品详情");
        }

        return toProductDetailDTO(product);
    }

    /**
     * 当前用户编辑自己的商品
     */
    @Override
    public ProductDetailDTO updateMyProduct(Long currentUserId,
                                            Long productId,
                                            ProductUpdateRequest request) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品不存在或已被删除");
        }

        // 权限校验
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException("无权修改该商品");
        }

        // 状态校验
        ProductStatus status = ProductStatus.fromDbValue(product.getStatus());
        if (status == ProductStatus.SOLD) {
            throw new BusinessException("商品已售出，不能编辑");
        }
        if (status != ProductStatus.ON_SHELF && status != ProductStatus.UNDER_REVIEW) {
            throw new BusinessException("当前状态不允许编辑");
        }

        // 更新字段
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setUpdateTime(LocalDateTime.now());

        // 修改后重新进入审核中
        product.setStatus(ProductStatus.UNDER_REVIEW.getDbValue());

        productMapper.updateProduct(product);

        return toProductDetailDTO(product);
    }

    @Override
    public void offShelfProductStatus(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }

        // 1. 权限校验：只能下架自己的商品
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException("无权操作该商品");
        }

        // 2. 状态流转校验：只允许审核中/上架 → 下架
        ProductStatus current = ProductStatus.fromDbValue(product.getStatus());
        if (current != ProductStatus.UNDER_REVIEW && current != ProductStatus.ON_SHELF) {
            throw new BusinessException("当前状态不允许下架");
        }

        // 3. 状态更新
        product.setStatus(ProductStatus.OFF_SHELF.getDbValue());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        log.info("商品下架成功，用户ID: {}, 商品ID: {}", currentUserId, productId);
    }

    // ================== 私有工具方法 ==================

    private ProductDTO toProductDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setProductId(product.getId());
        dto.setProductName(product.getTitle());
        dto.setCategory(product.getCategory());
        dto.setStatus(product.getStatus());
        dto.setSubmitTime(product.getCreateTime());
        return dto;
    }

    private ProductDetailDTO toProductDetailDTO(Product product) {
        ProductDetailDTO dto = new ProductDetailDTO();
        dto.setProductId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStatus(product.getStatus());
        dto.setCategory(product.getCategory());
        dto.setCreateTime(product.getCreateTime());
        // dto.setSubmitTime(...) 如果有这个字段，按你的定义补
        // 图片、违规信息等可以后面再补充
        return dto;
    }
}