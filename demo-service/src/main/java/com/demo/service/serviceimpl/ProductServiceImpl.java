package com.demo.service.serviceimpl;

import com.demo.constant.CreditPolicyConstants;
import com.demo.constant.ProductMessageConstant;
import com.demo.constant.ProductReason;
import com.demo.context.BaseContext;
import com.demo.dto.admin.ForceOffShelfRequest;
import com.demo.dto.user.*;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.demo.enumeration.CreditLevel;
import com.demo.enumeration.ProductActionType;
import com.demo.enumeration.ProductStatus;
import com.demo.exception.BusinessException;
import com.demo.exception.ProductNotFoundException;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.ProductViolationMapper;
import com.demo.result.PageResult;
import com.demo.service.ProductAuditService;
import com.demo.service.CreditService;
import com.demo.service.ProductGovernanceEventService;
import com.demo.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private ProductAuditService productAuditService;

    @Autowired
    private CreditService creditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.demo.service.SensitiveWordService sensitiveWordService;

    @Autowired
    private ProductGovernanceEventService productGovernanceEventService;

    @Override
    public PageResult<ProductDTO> getPendingApprovalProducts(int page, int pageSize, String productName, String category, String status) {
        int safePage = page <= 0 ? 1 : page;
        int safePageSize = pageSize <= 0 ? 10 : pageSize;

        PageHelper.startPage(safePage, safePageSize);
        List<Product> products = productMapper.getPendingApprovalProducts(productName, category, status);
        PageInfo<Product> pageInfo = new PageInfo<>(products);

        List<ProductDTO> dtoList = pageInfo.getList().stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());

        return new PageResult<>(
                dtoList,
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }

    /**
     * 管理员审核商品。
     * 接口语义：
     * 1) isApproved=true  -> under_review -> on_sale
     * 2) isApproved=false -> under_review -> off_shelf（写入驳回原因）
     *
     * 统一迁移内核固定执行顺序：
     * 1) 权限校验（管理员接口默认由 /admin/** 链路前置）
     * 2) 状态校验
     * 3) 条件更新（id + current_status）
     * 4) 更新行数=0 时执行幂等回查（重复审核返回“已处理”）
     */
    @Override
    @Transactional
    public String approveProduct(Long productId, boolean isApproved, String reason) {
        Long operatorId = safeOperatorId(BaseContext.getCurrentId());

        if (Boolean.TRUE.equals(isApproved)) {
            TransitionResult result = transit(
                    productId,
                    null,
                    false,
                    ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                    ProductActionType.APPROVE,
                    EnumSet.of(ProductStatus.UNDER_REVIEW),
                    status -> status == ProductStatus.ON_SHELF,
                    "已处理",
                    this::buildApproveInvalidStatusMessage,
                    (product, currentStatus) -> productMapper.updateStatusAndReasonByCurrentStatus(
                            productId,
                            currentStatus.getDbValue(),
                            ProductStatus.ON_SHELF.getDbValue(),
                            null
                    )
            );
            // Day16 P0：审核状态变更审计落库（主事务内强一致）。
            recordTransitionAudit(
                    ProductActionType.APPROVE,
                    operatorId,
                    "admin",
                    result,
                    "approve",
                    null,
                    null
            );
            // Day16 Step5：审核通过成功后，投递 PRODUCT_REVIEWED 事件（异步站内信由消费者负责）。
            if (!result.isIdempotent()) {
                productGovernanceEventService.publishProductReviewed(
                        result.getProduct(),
                        "approve",
                        ProductStatus.UNDER_REVIEW.getDbValue(),
                        result.getProduct().getStatus(),
                        null
                );
            }
            return result.isIdempotent() ? result.getMessage() : "商品审核通过";
        }

        // 驳回场景：先去除首尾空白，再做非空与长度校验。
        String r = (reason == null) ? null : reason.trim();
        if (r == null || r.isEmpty()) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_REJECT_REASON_REQUIRED);
        }
        if (r.length() > 200) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_REJECT_REASON_TOO_LONG);
        }

        TransitionResult result = transit(
                productId,
                null,
                false,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.REJECT,
                EnumSet.of(ProductStatus.UNDER_REVIEW),
                status -> status == ProductStatus.OFF_SHELF,
                "已处理",
                this::buildRejectInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByCurrentStatus(
                        productId,
                        currentStatus.getDbValue(),
                        ProductStatus.OFF_SHELF.getDbValue(),
                        r
                )
        );
        // Day16 P0：审核驳回状态变更审计落库（携带驳回原因）。
        recordTransitionAudit(
                ProductActionType.REJECT,
                operatorId,
                "admin",
                result,
                "reject",
                r,
                null
        );
        // Day16 Step5：审核驳回成功后，投递 PRODUCT_REVIEWED 事件（携带驳回原因）。
        if (!result.isIdempotent()) {
            productGovernanceEventService.publishProductReviewed(
                    result.getProduct(),
                    "reject",
                    ProductStatus.UNDER_REVIEW.getDbValue(),
                    result.getProduct().getStatus(),
                    r
            );
        }
        return result.isIdempotent() ? result.getMessage() : "商品审核驳回";
    }

    /**
     * Day16 Step3：管理员强制下架。
     * 接口语义：PUT /admin/products/{productId}/force-off-shelf
     *
     * 核心规则：
     * 1) 允许 under_review/on_sale 执行强制下架到 off_shelf。
     * 2) 若当前已是 off_shelf，按幂等成功返回“商品已下架”。
     * 3) 强制下架成功后写入 products.reason（reasonText）。
     * 4) 强制下架成功后插入 product_status_audit_log（记录 before/after/action/operator/reason）。
     */
    @Override
    @Transactional
    public String forceOffShelfProduct(Long operatorId, Long productId, ForceOffShelfRequest request) {
        if (operatorId == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
        }
        if (request == null) {
            throw new BusinessException("强制下架参数不能为空");
        }
        String reasonCode = normalizeBlankToNull(request.getReasonCode());
        if (reasonCode == null) {
            throw new BusinessException("reasonCode 不能为空");
        }
        if (reasonCode.length() > 64) {
            throw new BusinessException("reasonCode 长度不能超过64");
        }
        String reasonText = normalizeBlankToNull(request.getReasonText());
        if (reasonText == null) {
            throw new BusinessException("reasonText 不能为空");
        }
        if (reasonText.length() > 255) {
            throw new BusinessException("reasonText 长度不能超过255");
        }
        String reportTicketNo = normalizeBlankToNull(request.getReportTicketNo());
        if (reportTicketNo != null && reportTicketNo.length() > 32) {
            throw new BusinessException("reportTicketNo 长度不能超过32");
        }

        TransitionResult result = transit(
                productId,
                operatorId,
                false,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.FORCE_OFF_SHELF,
                EnumSet.of(ProductStatus.UNDER_REVIEW, ProductStatus.ON_SHELF),
                status -> status == ProductStatus.OFF_SHELF,
                "商品已下架",
                this::buildForceOffShelfInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByCurrentStatus(
                        productId,
                        currentStatus.getDbValue(),
                        ProductStatus.OFF_SHELF.getDbValue(),
                        reasonText
                )
        );

        boolean statusChanged = !Objects.equals(result.getBeforeStatus(), result.getProduct().getStatus());

        // Day16 P0：强制下架真实生效时写审计（附带 reportTicketNo 扩展字段）。
        recordTransitionAudit(
                ProductActionType.FORCE_OFF_SHELF,
                operatorId,
                "admin",
                result,
                reasonCode,
                reasonText,
                buildForceOffShelfExtraJson(reportTicketNo)
        );

        // Day16 Step5：强制下架真实生效后，投递 PRODUCT_FORCE_OFF_SHELF 事件。
        if (!result.isIdempotent() && statusChanged) {
            productGovernanceEventService.publishProductForceOffShelf(
                    result.getProduct(),
                    operatorId,
                    result.getBeforeStatus(),
                    result.getProduct().getStatus(),
                    reasonCode,
                    reasonText,
                    reportTicketNo
            );
        }

        return result.isIdempotent() ? result.getMessage() : "强制下架成功";
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
        String beforeStatus = product.getStatus();
        product.setStatus(newStatus.getDbValue());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateProduct(product);

        // 后台“直接改状态”兜底审计（非 Day16 主链路，但属于状态变更）。
        if (!Objects.equals(beforeStatus, newStatus.getDbValue())) {
            productAuditService.record(
                    product.getId(),
                    "update_status",
                    safeOperatorId(BaseContext.getCurrentId()),
                    "admin",
                    beforeStatus,
                    newStatus.getDbValue(),
                    "manual_update",
                    null,
                    null
            );
        }

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
     * 核心规则（Day16 Step2）：
     * 1) 先校验卖家权限与状态合法性。
     * 2) 编辑后统一流转为 under_review，并清空 reason。
     * 3) 更新走条件更新（id + owner_id + current_status），避免并发误覆盖。
     * 4) 更新行数=0 时做幂等/并发回查。
     */
    @Override
    public ProductDetailDTO updateMyProduct(Long currentUserId,
                                            Long productId,
                                            ProductUpdateRequest request) {
        // Day13 Step6 - 敏感词检测（编辑时）
        String checkText = (request.getTitle() != null ? request.getTitle() : "") + " " +
                          (request.getDescription() != null ? request.getDescription() : "");
        if (sensitiveWordService.containsSensitiveWord(checkText)) {
            String matched = sensitiveWordService.getMatchedWords(checkText);
            log.warn("商品编辑包含敏感词：productId={}, words={}", productId, matched);
            // 高风险：阻断上架，保持 under_review
            throw new BusinessException(ProductMessageConstant.PRODUCT_CONTENT_SENSITIVE_SUBMIT + matched);
        }

        // images：null=不改；[] 或全空清空；否则 join 存库（按接口语义原样保留）。
        String imagesForUpdate = null;
        if (request.getImages() != null) {
            if (request.getImages().isEmpty()) {
                imagesForUpdate = "";
            } else {
                List<String> cleaned = request.getImages().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                imagesForUpdate = cleaned.isEmpty() ? "" : String.join(",", cleaned);
            }
        }

        final String finalImagesForUpdate = imagesForUpdate;

        TransitionResult result = transit(
                productId,
                currentUserId,
                true,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_EDIT,
                ProductActionType.EDIT,
                EnumSet.of(ProductStatus.ON_SHELF, ProductStatus.UNDER_REVIEW, ProductStatus.OFF_SHELF),
                null,
                null,
                this::buildEditInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateForEditByOwnerAndCurrentStatus(
                        productId,
                        currentUserId,
                        currentStatus.getDbValue(),
                        ProductStatus.UNDER_REVIEW.getDbValue(),
                        null,
                        request.getTitle(),
                        request.getDescription(),
                        request.getPrice(),
                        finalImagesForUpdate
                )
        );
        // Day16 P0：编辑动作审计（on_sale/off_shelf/under_review -> under_review）。
        recordTransitionAudit(
                ProductActionType.EDIT,
                safeOperatorId(currentUserId),
                "seller",
                result,
                "edit",
                null,
                null
        );
        return toProductDetailDTO(result.getProduct());
    }

    /**
     * 卖家主动下架（兼容 under_review/on_sale -> off_shelf）。
     * 幂等口径：重复下架返回“商品已下架”。
     */
    @Override
    public String offShelfProductStatus(Long currentUserId, Long productId) {
        TransitionResult result = transit(
                productId,
                currentUserId,
                true,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.OFF_SHELF,
                EnumSet.of(ProductStatus.UNDER_REVIEW, ProductStatus.ON_SHELF),
                status -> status == ProductStatus.OFF_SHELF,
                "商品已下架",
                status -> ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_OFF_SHELF,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByOwnerAndCurrentStatus(
                        productId,
                        currentUserId,
                        currentStatus.getDbValue(),
                        ProductStatus.OFF_SHELF.getDbValue(),
                        product.getReason()
                )
        );
        // Day16 P0：卖家下架审计。
        recordTransitionAudit(
                ProductActionType.OFF_SHELF,
                safeOperatorId(currentUserId),
                "seller",
                result,
                "seller_off_shelf",
                null,
                null
        );
        return result.isIdempotent() ? result.getMessage() : "下架成功";
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
     * 卖家重新提审（off_shelf -> under_review）。
     * 幂等口径：若当前已是 under_review，直接返回当前详情（视为成功）。
     */
    @Override
    public ProductDetailDTO resubmitProduct(Long currentUserId, Long productId) {
        TransitionResult result = transit(
                productId,
                currentUserId,
                true,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.RESUBMIT,
                EnumSet.of(ProductStatus.OFF_SHELF),
                status -> status == ProductStatus.UNDER_REVIEW,
                null,
                this::buildResubmitInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByOwnerAndCurrentStatus(
                        productId,
                        currentUserId,
                        currentStatus.getDbValue(),
                        ProductStatus.UNDER_REVIEW.getDbValue(),
                        null
                )
        );
        // Day16 P0：卖家重提审核审计。
        recordTransitionAudit(
                ProductActionType.RESUBMIT,
                safeOperatorId(currentUserId),
                "seller",
                result,
                "resubmit",
                null,
                null
        );
        return toProductDetailDTO(result.getProduct());
    }

    /**
     * 卖家上架入口（Day16 语义冻结：与 resubmit 等价，仅提审，不直上架）。
     * 幂等口径：若当前已是 under_review，直接返回当前详情（视为成功）。
     */
    @Override
    public ProductDetailDTO onShelfProduct(Long currentUserId, Long productId) {
        TransitionResult result = transit(
                productId,
                currentUserId,
                true,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.ON_SHELF,
                EnumSet.of(ProductStatus.OFF_SHELF),
                status -> status == ProductStatus.UNDER_REVIEW,
                null,
                this::buildOnShelfInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByOwnerAndCurrentStatus(
                        productId,
                        currentUserId,
                        currentStatus.getDbValue(),
                        ProductStatus.UNDER_REVIEW.getDbValue(),
                        null
                )
        );
        // Day16 P0：on-shelf 入口（提审别名）审计。
        recordTransitionAudit(
                ProductActionType.ON_SHELF,
                safeOperatorId(currentUserId),
                "seller",
                result,
                "on_shelf_alias_resubmit",
                null,
                null
        );
        return toProductDetailDTO(result.getProduct());
    }

    /**
     * 卖家撤回审核（under_review -> off_shelf）。
     */
    @Override
    public ProductDetailDTO withdrawProduct(Long currentUserId, Long productId) {
        TransitionResult result = transit(
                productId,
                currentUserId,
                true,
                ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE,
                ProductActionType.WITHDRAW,
                EnumSet.of(ProductStatus.UNDER_REVIEW),
                null,
                null,
                this::buildWithdrawInvalidStatusMessage,
                (product, currentStatus) -> productMapper.updateStatusAndReasonByOwnerAndCurrentStatus(
                        productId,
                        currentUserId,
                        currentStatus.getDbValue(),
                        ProductStatus.OFF_SHELF.getDbValue(),
                        ProductReason.SELLER_WITHDRAW
                )
        );
        // Day16 P0：卖家撤回审核审计。
        recordTransitionAudit(
                ProductActionType.WITHDRAW,
                safeOperatorId(currentUserId),
                "seller",
                result,
                ProductReason.SELLER_WITHDRAW,
                ProductReason.SELLER_WITHDRAW,
                null
        );
        return toProductDetailDTO(result.getProduct());
    }

    /**
     * 商品状态迁移统一内核（Step2 核心）。
     *
     * 固定执行顺序（所有动作统一）：
     * 1. 权限校验：owner 动作强制校验 owner_id == operatorId。
     * 2. 状态校验：判断是否命中允许的 from 状态；不满足时按动作返回对应业务错误。
     * 3. 条件更新：执行 id + current_status (+ owner_id) 的并发安全更新。
     * 4. 幂等回查：当 rows=0 时回查最新状态，若命中幂等条件则按成功返回，否则抛业务错误。
     *
     * 设计目的：
     * - 把状态迁移规则收敛到一个入口，避免 approve/reject/off_shelf/... 各自维护一套分叉逻辑。
     * - 通过 current_status 条件更新避免并发写覆盖，保证状态机行为可预测。
     */
    private TransitionResult transit(Long productId,
                                     Long operatorId,
                                     boolean ownerAction,
                                     String permissionDeniedMessage,
                                     ProductActionType actionType,
                                     Set<ProductStatus> allowedFromStatuses,
                                     java.util.function.Predicate<ProductStatus> idempotentPredicate,
                                     String idempotentMessage,
                                     java.util.function.Function<ProductStatus, String> invalidStatusMessageBuilder,
                                     TransitionUpdater updater) {
        Product product = mustGetProduct(productId);

        // 1) 权限校验：卖家动作必须 owner 自己操作。
        if (ownerAction && !Objects.equals(product.getOwnerId(), operatorId)) {
            throw new BusinessException(permissionDeniedMessage);
        }

        // 2) 状态校验：先做“幂等短路”，再判定是否允许迁移。
        ProductStatus currentStatus = ProductStatus.fromDbValue(product.getStatus());
        if (idempotentPredicate != null && idempotentPredicate.test(currentStatus)) {
            return TransitionResult.idempotent(product, idempotentMessage, currentStatus.getDbValue());
        }
        if (!allowedFromStatuses.contains(currentStatus)) {
            throw new BusinessException(invalidStatusMessageBuilder.apply(currentStatus));
        }

        // 3) 条件更新：id + current_status (+ owner_id)。
        int rows = updater.update(product, currentStatus);
        if (rows == 1) {
            Product latest = mustGetProduct(productId);
            log.info("商品状态迁移成功: action={}, productId={}, from={}, to={}",
                    actionType.getCode(), productId, currentStatus.getDbValue(), latest.getStatus());
            return TransitionResult.success(latest, currentStatus.getDbValue());
        }

        // 4) 行数=0 幂等回查：处理并发下“别人先更新”场景。
        Product latest = mustGetProduct(productId);
        ProductStatus latestStatus = ProductStatus.fromDbValue(latest.getStatus());
        if (idempotentPredicate != null && idempotentPredicate.test(latestStatus)) {
            log.info("商品状态迁移幂等命中: action={}, productId={}, latestStatus={}",
                    actionType.getCode(), productId, latestStatus.getDbValue());
            return TransitionResult.idempotent(latest, idempotentMessage, latestStatus.getDbValue());
        }
        if (!allowedFromStatuses.contains(latestStatus)) {
            throw new BusinessException(invalidStatusMessageBuilder.apply(latestStatus));
        }
        throw new BusinessException(ProductMessageConstant.PRODUCT_STATUS_UPDATE_FAILED);
    }

    /**
     * 查询商品，不存在则抛统一业务异常。
     */
    private Product mustGetProduct(Long productId) {
        Product product = productMapper.getProductById(productId);
        if (product == null || product.getIsDeleted() == 1) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_DELETED);
        }
        return product;
    }

    /**
     * approve 的非法状态文案映射。
     */
    private String buildApproveInvalidStatusMessage(ProductStatus status) {
        return ProductMessageConstant.PRODUCT_ONLY_UNDER_REVIEW_CAN_APPROVE + status.getDbValue();
    }

    /**
     * reject 的非法状态文案映射。
     */
    private String buildRejectInvalidStatusMessage(ProductStatus status) {
        return ProductMessageConstant.PRODUCT_ONLY_UNDER_REVIEW_CAN_REJECT + status.getDbValue();
    }

    /**
     * edit 的非法状态文案映射。
     */
    private String buildEditInvalidStatusMessage(ProductStatus status) {
        if (status == ProductStatus.SOLD) {
            return ProductMessageConstant.PRODUCT_SOLD_CANNOT_EDIT;
        }
        return ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_EDIT;
    }

    /**
     * resubmit 的非法状态文案映射。
     */
    private String buildResubmitInvalidStatusMessage(ProductStatus status) {
        if (status == ProductStatus.SOLD) {
            return ProductMessageConstant.PRODUCT_SOLD_CANNOT_RESUBMIT;
        }
        if (status == ProductStatus.ON_SHELF) {
            return ProductMessageConstant.PRODUCT_ON_SALE_NO_NEED_RESUBMIT;
        }
        return ProductMessageConstant.PRODUCT_STATUS_CANNOT_RESUBMIT;
    }

    /**
     * on-shelf(提审别名) 的非法状态文案映射。
     */
    private String buildOnShelfInvalidStatusMessage(ProductStatus status) {
        if (status == ProductStatus.SOLD) {
            return ProductMessageConstant.PRODUCT_SOLD_CANNOT_ON_SHELF;
        }
        if (status == ProductStatus.ON_SHELF) {
            return ProductMessageConstant.PRODUCT_ON_SALE_NO_NEED_ON_SHELF;
        }
        return ProductMessageConstant.PRODUCT_STATUS_CANNOT_ON_SHELF;
    }

    /**
     * withdraw 的非法状态文案映射。
     */
    private String buildWithdrawInvalidStatusMessage(ProductStatus status) {
        if (status == ProductStatus.SOLD) {
            return ProductMessageConstant.PRODUCT_SOLD_CANNOT_WITHDRAW;
        }
        if (status == ProductStatus.ON_SHELF) {
            return ProductMessageConstant.PRODUCT_ON_SALE_NEED_OFF_SHELF_FIRST;
        }
        if (status == ProductStatus.OFF_SHELF) {
            return ProductMessageConstant.PRODUCT_ALREADY_WITHDRAWN;
        }
        return ProductMessageConstant.PRODUCT_STATUS_NO_NEED_WITHDRAW;
    }

    /**
     * force_off_shelf 的非法状态文案映射。
     */
    private String buildForceOffShelfInvalidStatusMessage(ProductStatus status) {
        // Day16 不允许 sold 回流到 off_shelf。
        if (status == ProductStatus.SOLD) {
            return ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_FORCE_OFF_SHELF;
        }
        return ProductMessageConstant.PRODUCT_STATUS_NOT_ALLOW_FORCE_OFF_SHELF;
    }

    /**
     * 状态迁移成功后统一写审计日志（非幂等分支）。
     * 说明：
     * 1) 幂等命中（重复请求）不重复写审计，避免日志污染。
     * 2) beforeStatus 取统一迁移内核返回值，保证与条件更新前状态一致。
     */
    private void recordTransitionAudit(ProductActionType actionType,
                                       Long operatorId,
                                       String operatorRole,
                                       TransitionResult result,
                                       String reasonCode,
                                       String reasonText,
                                       String extraJson) {
        if (result == null || result.isIdempotent()) {
            return;
        }
        productAuditService.record(
                result.getProduct().getId(),
                actionType.getCode(),
                safeOperatorId(operatorId),
                operatorRole,
                result.getBeforeStatus(),
                result.getProduct().getStatus(),
                reasonCode,
                reasonText,
                extraJson
        );
    }

    /**
     * 组装强制下架扩展信息 JSON。
     */
    private String buildForceOffShelfExtraJson(String reportTicketNo) {
        if (reportTicketNo == null) {
            return null;
        }
        java.util.Map<String, String> extra = new java.util.HashMap<>();
        extra.put("reportTicketNo", reportTicketNo);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException e) {
            throw new BusinessException("强制下架审计扩展信息序列化失败");
        }
    }

    /**
     * 把空白字符串归一化为 null，便于可选字段落库。
     */
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 统一兜底操作人 ID，避免审计因空操作人导致写入失败。
     */
    private Long safeOperatorId(Long operatorId) {
        return operatorId == null ? 0L : operatorId;
    }

    /**
     * 条件更新执行器：把具体 SQL 更新逻辑以函数方式注入统一内核。
     */
    @FunctionalInterface
    private interface TransitionUpdater {
        int update(Product product, ProductStatus currentStatus);
    }

    /**
     * 状态迁移返回结果：
     * - product：迁移后（或幂等命中时当前）商品快照
     * - idempotent：是否命中幂等分支
     * - message：幂等提示文案（非幂等时可为空）
     */
    private static class TransitionResult {
        private final Product product;
        private final boolean idempotent;
        private final String message;
        private final String beforeStatus;

        private TransitionResult(Product product, boolean idempotent, String message, String beforeStatus) {
            this.product = product;
            this.idempotent = idempotent;
            this.message = message;
            this.beforeStatus = beforeStatus;
        }

        static TransitionResult success(Product product, String beforeStatus) {
            return new TransitionResult(product, false, null, beforeStatus);
        }

        static TransitionResult idempotent(Product product, String message, String beforeStatus) {
            return new TransitionResult(product, true, message, beforeStatus);
        }

        Product getProduct() {
            return product;
        }

        boolean isIdempotent() {
            return idempotent;
        }

        String getMessage() {
            return message;
        }

        String getBeforeStatus() {
            return beforeStatus;
        }
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



