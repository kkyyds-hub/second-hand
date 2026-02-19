package com.demo.service.serviceimpl;

import com.demo.constant.CreditPolicyConstants;
import com.demo.constant.ProductMessageConstant;
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
/**
 * ProductServiceImpl 业务组件。
 */
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductViolationMapper productViolationMapper;

    @Autowired
    private CreditService creditService;

    @Autowired
    private com.demo.service.SensitiveWordService sensitiveWordService;

    @Override
    public PageResult<ProductDTO> getPendingApprovalProducts(int page, int pageSize, String productName, String category, String status) {
        return null;
    }

    /**
     * 管理员审核商品。
     * 1. isApproved=true：审核通过，商品状态更新为 ON_SHELF。
     * 2. isApproved=false：审核拒绝，商品状态更新为 OFF_SHELF，并记录驳回原因。
     */
    @Override
    @Transactional
    public void approveProduct(Long productId, boolean isApproved, String reason) {

        if (Boolean.TRUE.equals(isApproved)) {
            int rows = productMapper.updateStatusAndReasonIfUnderReview(
                    productId,
                    ProductStatus.ON_SHELF.getDbValue(), // 数据库存储值为 on_sale
                    null
            );
            if (rows == 0) {
                // 可选：为了返回更精确的错误原因，再补查一次商品当前状态。
                Product p = productMapper.getProductById(productId);
                if (p == null || p.getIsDeleted() == 1) throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
                throw new BusinessException(ProductMessageConstant.PRODUCT_ONLY_UNDER_REVIEW_CAN_APPROVE + p.getStatus());
            }
            return;
        }
        // 驳回场景：先去除首尾空白，再做非空与长度校验。
        String r = (reason == null) ? null : reason.trim();
        if (r == null || r.isEmpty()) throw new BusinessException(ProductMessageConstant.PRODUCT_REJECT_REASON_REQUIRED);
        if (r.length() > 200) throw new BusinessException(ProductMessageConstant.PRODUCT_REJECT_REASON_TOO_LONG);

        int rows = productMapper.updateStatusAndReasonIfUnderReview(
                productId,
                ProductStatus.OFF_SHELF.getDbValue(),
                r
        );
        if (rows == 0) {
            Product p = productMapper.getProductById(productId);
            if (p == null || p.getIsDeleted() == 1) throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
            throw new BusinessException(ProductMessageConstant.PRODUCT_ONLY_UNDER_REVIEW_CAN_REJECT + p.getStatus());
        }
    }



    /**
     * 查询商品的违规记录。
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

        log.info("商品状态更新成功，商品 ID: {}, 新状态: {}", productId, newStatus);
    }

    /**
     * 查询用户自己的商品列表。
     */
    @Override
    public PageResult<Product> getUserProducts(UserProductQueryDTO queryDTO) {
        // 这里也可以直接用 BaseContext.getCurrentId()，避免前端传 userId
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
     * 查询当前登录用户的某个商品详情。
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
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_VIEW);
        }

        return toProductDetailDTO(product);
    }

    /**
     * 当前用户编辑自己的商品。
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
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_EDIT);
        }

        // 状态校验：sold 禁止编辑；on_sale/under_review/off_shelf 允许编辑
        ProductStatus status = ProductStatus.fromDbValue(product.getStatus());
        if (status == ProductStatus.SOLD) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_SOLD_CANNOT_EDIT);
        }
        if (status != ProductStatus.ON_SHELF
                && status != ProductStatus.UNDER_REVIEW
                && status != ProductStatus.OFF_SHELF) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_EDIT);
        }

        // 更新字段
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        // Day13 Step6 - 敏感词检测（编辑时）
        String checkText = (request.getTitle() != null ? request.getTitle() : "") + " " +
                          (request.getDescription() != null ? request.getDescription() : "");
        if (sensitiveWordService.containsSensitiveWord(checkText)) {
            String matched = sensitiveWordService.getMatchedWords(checkText);
            log.warn("商品编辑包含敏感词：productId={}, words={}", productId, matched);
            // 高风险：阻断上架，保持 under_review
            throw new BusinessException(ProductMessageConstant.PRODUCT_CONTENT_SENSITIVE_SUBMIT + matched);
        }

        // images：null=不改；[] 或全空清空；否则 join 存库
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

        // 编辑后统一进入审核中，并清空历史驳回原因（否则前端会一直显示旧 reason）。
        productMapper.updateStatusAndReason(productId, ProductStatus.UNDER_REVIEW.getDbValue(), null);

        Product dbProduct = productMapper.getProductById(productId);
        if (dbProduct == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_UPDATE_RETRY);
        }
        return toProductDetailDTO(dbProduct);
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    public void offShelfProductStatus(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new ProductNotFoundException("商品未找到，ID: " + productId);
        }

        // 1. 权限校验：只能下架自己的商品
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }

        // 2. 状态流转校验：只允许审核中/上架 -> 下架
        ProductStatus current = ProductStatus.fromDbValue(product.getStatus());
        if (current != ProductStatus.UNDER_REVIEW && current != ProductStatus.ON_SHELF) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_OFF_SHELF);
        }

        // 3. 状态更新
        product.setStatus(ProductStatus.OFF_SHELF.getDbValue());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        log.info("商品下架成功，用户 ID: {}, 商品 ID: {}", currentUserId, productId);
    }

    /**
     * 创建或新增相关数据。
     */
    @Override
    public ProductDetailDTO createProduct(Long currentUserId, ProductCreateRequest request) {
        // Day13 Step6 - 敏感词检测（创建时）
        String checkText = (request.getTitle() != null ? request.getTitle() : "") + " " +
                          (request.getDescription() != null ? request.getDescription() : "");
        if (sensitiveWordService.containsSensitiveWord(checkText)) {
            String matched = sensitiveWordService.getMatchedWords(checkText);
            log.warn("商品创建包含敏感词：userId={}, words={}", currentUserId, matched);
            // 高风险：阻断发布
            throw new BusinessException(ProductMessageConstant.PRODUCT_CONTENT_SENSITIVE_CREATE + matched);
        }

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
        // ===== Day10：发布信用策略（P0）====
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
            throw new BusinessException(ProductMessageConstant.PRODUCT_CREATE_FAILED_RETRY);
        }

        return toProductDetailDTO(dbProduct);

    }

    /**
     * 查询并返回相关结果。
     */
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

    /**
     * 查询并返回相关结果。
     */
    @Override
    public MarketProductDetailDTO getMarketProductDetail(Long productId) {
        if (productId == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_ID_REQUIRED);
        }

        Product product = productMapper.getMarketProductById(productId);
        if (product == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
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

    /**
     * 删除或清理相关数据。
     */
    @Override
    public void deleteMyProduct(Long currentUserId, Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null){
            throw new ProductNotFoundException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
        }
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }
        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());
        if (st == ProductStatus.SOLD) throw new BusinessException(ProductMessageConstant.PRODUCT_SOLD_CANNOT_DELETE);
        if (st == ProductStatus.ON_SHELF) throw new BusinessException(ProductMessageConstant.PRODUCT_ON_SALE_DELETE_NEED_OFF_SHELF);

        // 执行软删除更新。
        int rows = productMapper.softDeleteByOwner(productId, currentUserId);
        if (rows == 0) throw new BusinessException(ProductMessageConstant.PRODUCT_DELETE_FAILED_RETRY);
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    public ProductDetailDTO resubmitProduct(Long currentUserId, Long productId) {
        // Day16：标准“重新提交审核”入口（off_shelf -> under_review）。
        Product product = productMapper.getProductById(productId);
        if (product == null) throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
        if (!Objects.equals(product.getOwnerId(), currentUserId)) throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());

        if (st == ProductStatus.SOLD) throw new BusinessException(ProductMessageConstant.PRODUCT_SOLD_CANNOT_RESUBMIT);
        if (st == ProductStatus.ON_SHELF) throw new BusinessException(ProductMessageConstant.PRODUCT_ON_SALE_NO_NEED_RESUBMIT);

        // 已是待审核状态，直接返回当前详情。
        if (st == ProductStatus.UNDER_REVIEW) {
            return toProductDetailDTO(product);
        }

        // 仅允许下架状态发起重新提交。
        if (st != ProductStatus.OFF_SHELF) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_CANNOT_RESUBMIT);
        }

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId, currentUserId,
                ProductStatus.UNDER_REVIEW.getDbValue(),
                null
        );
        if (rows == 0) throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_UPDATE_FAILED);

        Product db = productMapper.getProductById(productId);
        return toProductDetailDTO(db);
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    public ProductDetailDTO onShelfProduct(Long currentUserId, Long productId) {
        // Day16：兼容入口，语义与 resubmit 一致（不是 off_shelf -> on_sale 直上架）。
        Product product = productMapper.getProductById(productId);
        if (product == null) throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
        if (!Objects.equals(product.getOwnerId(), currentUserId)) throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());

        if (st == ProductStatus.SOLD) throw new BusinessException(ProductMessageConstant.PRODUCT_SOLD_CANNOT_ON_SHELF);
        if (st == ProductStatus.ON_SHELF) throw new BusinessException(ProductMessageConstant.PRODUCT_ON_SALE_NO_NEED_ON_SHELF);

        // 已是待审核状态，直接返回当前详情。
        if (st == ProductStatus.UNDER_REVIEW) {
            return toProductDetailDTO(product);
        }

        // 仅允许下架状态发起重新提交。
        if (st != ProductStatus.OFF_SHELF) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_CANNOT_ON_SHELF);
        }

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId, currentUserId,
                ProductStatus.UNDER_REVIEW.getDbValue(),
                null
        );
        if (rows == 0) throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_UPDATE_FAILED);

        Product db = productMapper.getProductById(productId);
        return toProductDetailDTO(db);
    }

    /**
     * 更新相关业务状态。
     */
    @Override
    public ProductDetailDTO withdrawProduct(Long currentUserId, Long productId) {
        // Day16：撤回审核入口（under_review -> off_shelf）。
        Product product = productMapper.getProductById(productId);
        if (product == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
        }
        if (!Objects.equals(product.getOwnerId(), currentUserId)) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }

        ProductStatus st = ProductStatus.fromDbValue(product.getStatus());
        // sold：不允许撤回。
        if (st == ProductStatus.SOLD) throw new BusinessException(ProductMessageConstant.PRODUCT_SOLD_CANNOT_WITHDRAW);
        // on_sale：提示应先走下架流程。
        if (st == ProductStatus.ON_SHELF) throw new BusinessException(ProductMessageConstant.PRODUCT_ON_SALE_NEED_OFF_SHELF_FIRST);
        // off_shelf：无需重复撤回。
        if (st == ProductStatus.OFF_SHELF) throw new BusinessException(ProductMessageConstant.PRODUCT_ALREADY_WITHDRAWN);
        // 仅 under_review 允许继续执行撤回。
        if (st != ProductStatus.UNDER_REVIEW) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_NO_NEED_WITHDRAW);
        }
        // reason：固定记录卖家撤回原因，便于审计。
        String reason = "seller_withdraw";

        // 复用状态更新接口落库。

        int rows = productMapper.updateStatusAndReasonByOwner(
                productId,
                currentUserId,
                ProductStatus.OFF_SHELF.getDbValue(),
                reason
        );
        if (rows == 0) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_UPDATE_FAILED);
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



