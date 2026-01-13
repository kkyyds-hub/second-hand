package com.demo.service.serviceimpl;

import com.demo.constant.CreditPolicyConstants;
import com.demo.context.BaseContext;
import com.demo.dto.user.*;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.demo.enumeration.CreditLevel;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.exception.ProductNotFoundException;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.ProductViolationMapper;
import com.demo.result.PageResult;
import com.demo.service.CreditService;
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

    @Autowired
    private CreditService creditService;

    /**
     * 审核列表：分页查询待审核 / 已审核商品
     */
    @Override
    public PageResult<ProductDTO> getPendingApprovalProducts(int page,
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
        PageInfo<ProductDTO> pageInfo = new PageInfo<>(dtoList);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }

    /**
     * 管理员审核商品
     * isApproved = true  -> 审核通过，商品上架（ON_SHELF）
     * isApproved = false -> 审核拒绝，商品下架（OFF_SHELF），记录原因
     */
    @Override
    @Transactional
    public void approveProduct(Long productId, boolean isApproved, String reason) {

        if (Boolean.TRUE.equals(isApproved)) {
            int rows = productMapper.updateStatusAndReasonIfUnderReview(
                    productId,
                    ProductStatus.ON_SHELF.getDbValue(), // dbValue = on_sale
                    null
            );
            if (rows == 0) {
                // 可选：为了错误更精确，再查一次
                Product p = productMapper.getProductById(productId);
                if (p == null || p.getIsDeleted() == 1) throw new BusinessException("商品不存在或已被删除");
                throw new BusinessException("仅审核中商品可通过，当前状态: " + p.getStatus());
            }
            return;
        }

        // reject：trim + 校验
        String r = (reason == null) ? null : reason.trim();
        if (r == null || r.isEmpty()) throw new BusinessException("驳回原因不能为空");
        if (r.length() > 200) throw new BusinessException("驳回原因长度不能超过200");

        int rows = productMapper.updateStatusAndReasonIfUnderReview(
                productId,
                ProductStatus.OFF_SHELF.getDbValue(),
                r
        );
        if (rows == 0) {
            Product p = productMapper.getProductById(productId);
            if (p == null || p.getIsDeleted() == 1) throw new BusinessException("商品不存在或已被删除");
            throw new BusinessException("仅审核中商品可驳回，当前状态: " + p.getStatus());
        }
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
    public PageResult<Product> getUserProducts(UserProductQueryDTO queryDTO) {
        // 这里你也可以直接用 BaseContext.getCurrentId()，避免前端传 userId
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<Product> products =
                productMapper.getUserProducts(queryDTO.getUserId(), queryDTO.getStatus());

        PageInfo<Product> pageInfo = new PageInfo<>(products);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
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

        // 状态校验：sold 禁止编辑；on_sale/under_review/off_shelf 允许编辑
        ProductStatus status = ProductStatus.fromDbValue(product.getStatus());
        if (status == ProductStatus.SOLD) {
            throw new BusinessException("商品已售出，不能编辑");
        }
        if (status != ProductStatus.ON_SHELF
                && status != ProductStatus.UNDER_REVIEW
                && status != ProductStatus.OFF_SHELF) {
            throw new BusinessException("当前状态不允许编辑");
        }

        // 更新字段
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        // images：null=不改；[] 或全空=清空；否则 join 存库
        if (request.getImages() != null) {
            if (request.getImages().isEmpty()) {
                product.setImages("");
            } else {
                List<String> cleaned = request.getImages().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                product.setImages(cleaned.isEmpty() ? "" : String.join(",", cleaned));
            }
        }

        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        // 编辑后统一进入审核中，并清空历史驳回原因（否则前端会一直显示旧 reason）
        productMapper.updateStatusAndReason(productId, ProductStatus.UNDER_REVIEW.getDbValue(), null);

        Product dbProduct = productMapper.getProductById(productId);
        if (dbProduct == null) {
            throw new BusinessException("更新失败，请重试");
        }
        return toProductDetailDTO(dbProduct);
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

    @Override
    public ProductDetailDTO createProduct(Long currentUserId, ProductCreateRequest request) {
        Product product = new Product();
        product.setOwnerId(currentUserId);
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());

        // 2) images: List<String> -> "a,b,c"
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            product.setImages(String.join(",", request.getImages()));
        } else {
            product.setImages(null);
        }
        product.setStatus(ProductStatus.UNDER_REVIEW.getDbValue());
        product.setViewCount(0);
        product.setReason(null);
        // ===== Day10：发布信用策略（P0）=====
        UserCreditDTO credit = creditService.getCredit(currentUserId);
        CreditLevel level = CreditLevel.fromDbValue(credit.getCreditLevel());

        // LV1：禁止发布
        if (level == CreditLevel.LV1) {
            throw new BusinessException("信用等级过低（LV1），暂不可发布商品");
        }

        // LV2：限制活跃商品数
        if (level == CreditLevel.LV2) {
            long activeCount = productMapper.countActiveProductsByOwnerId(currentUserId);
            if (activeCount >= CreditPolicyConstants.MAX_ACTIVE_PRODUCTS_LV2) {
                throw new BusinessException("信用等级为 LV2，活跃商品数量已达上限：" + CreditPolicyConstants.MAX_ACTIVE_PRODUCTS_LV2);
            }
        }

        // 4) 落库（useGeneratedKeys 会把 product.id 回填）
        productMapper.insertProduct(product);
        Product dbProduct = productMapper.getProductById(product.getId());
        if (dbProduct == null) {
            throw new BusinessException("商品创建失败，请重试");
        }

        return toProductDetailDTO(dbProduct);

    }

    @Override
    public PageResult<MarketProductSummaryDTO> getMarketProductList(MarketProductQueryDTO queryDTO) {
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getPageSize());
        List<Product> productList = productMapper.getMarketProductList(queryDTO.getKeyword(), queryDTO.getCategory());
        PageInfo<Product> pageInfo = new PageInfo<>(productList);

        List<MarketProductSummaryDTO> summaryList = productList.stream()
                .map(p -> {
                    MarketProductSummaryDTO dto = new MarketProductSummaryDTO();
                    dto.setProductId(p.getId());
                    dto.setTitle(p.getTitle());
                    dto.setPrice(p.getPrice());
                    dto.setCategory(p.getCategory());
                    dto.setOwnerId(p.getOwnerId());
                    dto.setCreateTime(p.getCreateTime());
                    dto.setThumbnail(extractFirstImage(p.getImages()));
                    return dto;
                })
                .collect(java.util.stream.Collectors.toList()); // 更兼容（避免 JDK 版本问题）

        return new PageResult<>(
                summaryList,
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }

    @Override
    public MarketProductDetailDTO getMarketProductDetail(Long productId) {
        if (productId == null) {
            throw new BusinessException("productId 不能为空");
        }

        Product product = productMapper.getMarketProductById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在或不可查看");
        }

        MarketProductDetailDTO dto = new MarketProductDetailDTO();
        dto.setProductId(product.getId());
        dto.setTitle(product.getTitle());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setCategory(product.getCategory());
        dto.setOwnerId(product.getOwnerId());
        dto.setCreateTime(product.getCreateTime());
        dto.setImageUrls(splitImages(product.getImages()));
        return dto;
    }

    @Override
    public void deleteMyProduct(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null){
            throw new ProductNotFoundException("商品不存在或已被删除");
        }
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException("无权操作该商品");
        }
        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());
        if (st == ProductStatus.SOLD) throw new BusinessException("已售商品不可删除");
        if (st == ProductStatus.ON_SHELF) throw new BusinessException("在售商品请先下架再删除");

        // 4. db update (soft delete)
        int rows = productMapper.softDeleteByOwner(productId, currentUserId);
        if (rows == 0) throw new BusinessException("删除失败，请重试");
    }

    @Override
    public ProductDetailDTO resubmitProduct(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null) throw new BusinessException("商品不存在或已被删除");
        if (!Objects.equals(product.getOwnerId(), currentUserId)) throw new BusinessException("无权操作该商品");

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());

        if (st == ProductStatus.SOLD) throw new BusinessException("已售商品不可提审");
        if (st == ProductStatus.ON_SHELF) throw new BusinessException("在售商品无需提审");

        //已是审核中，直接返回
        if (st == ProductStatus.UNDER_REVIEW) {
            return toProductDetailDTO(product);
        }

        // 只允许下架状态发起重提审
        if (st != ProductStatus.OFF_SHELF) {
            throw new BusinessException("当前状态无法重新提交审核");
        }

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId, currentUserId,
                ProductStatus.UNDER_REVIEW.getDbValue(),
                null
        );
        if (rows == 0) throw new BusinessException("商品状态更新失败");

        Product db = productMapper.getProductById(productId);
        return toProductDetailDTO(db);
    }

    @Override
    public ProductDetailDTO onShelfProduct(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null) throw new BusinessException("商品不存在或已被删除");
        if (!Objects.equals(product.getOwnerId(), currentUserId)) throw new BusinessException("无权操作该商品");

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());

        if (st == ProductStatus.SOLD) throw new BusinessException("已售商品不可上架");
        if (st == ProductStatus.ON_SHELF) throw new BusinessException("在售商品无需上架");

        //已是审核中，直接返回
        if (st == ProductStatus.UNDER_REVIEW) {
            return toProductDetailDTO(product);
        }

        // 只允许下架状态发起重提审
        if (st != ProductStatus.OFF_SHELF) {
            throw new BusinessException("当前状态无法重新上架");
        }

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId, currentUserId,
                ProductStatus.UNDER_REVIEW.getDbValue(),
                null
        );
        if (rows == 0) throw new BusinessException("商品状态更新失败");

        Product db = productMapper.getProductById(productId);
        return toProductDetailDTO(db);
    }

    @Override
    public ProductDetailDTO withdrawProduct(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new BusinessException("商品不存在或已被删除");
        }
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException("无权操作该商品");
        }

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());
        // - sold：报错
        if (st == ProductStatus.SOLD) throw new BusinessException("已售商品不可撤回");
        // - on_sale：报错（提示要下架请用 off-shelf）
        if (st == ProductStatus.ON_SHELF) throw new BusinessException("在售商品需先下架");
        // - off_shelf：报错（无需撤回）
        if (st == ProductStatus.OFF_SHELF) throw new BusinessException("商品已撤回");
        // - under_review：允许继续往下走
        // - status：ProductStatus.OFF_SHELF.getDbValue()
        if (st != ProductStatus.UNDER_REVIEW) {
            throw new BusinessException("当前状态无需撤回审核");
        }
        // - reason：建议固定字符串，如 "seller_withdraw"（或你统一的常量）
        String reason = "seller_withdraw";

        // - 复用：productMapper.updateStatusAndReasonByOwner(...)

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId,
                currentUserId,
                ProductStatus.OFF_SHELF.getDbValue(),
                reason
        );
        if (rows == 0) {
            throw new BusinessException("商品状态更新失败");
        }

        Product db = productMapper.getProductById(productId);
        return toProductDetailDTO(db);
    }



    private String extractFirstImage(String images) {
        if (images == null || images.isBlank()) return null;
        for (String s : images.split(",")) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    private java.util.List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toList());
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
        dto.setImageUrls(splitImages(product.getImages()));
        dto.setStatus(product.getStatus());
        dto.setCategory(product.getCategory());
        dto.setCreateTime(product.getCreateTime());
        dto.setUpdateTime(product.getUpdateTime());
        dto.setReviewRemark(product.getReason());
        dto.setSubmitTime(product.getUpdateTime() != null ? product.getUpdateTime() : product.getCreateTime());
        return dto;
    }

}