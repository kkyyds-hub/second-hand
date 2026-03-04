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
import com.demo.security.InputSecurityGuard;
import com.demo.result.PageResult;
import com.demo.service.ProductAuditService;
import com.demo.service.CreditService;
import com.demo.service.ProductGovernanceEventService;
import com.demo.service.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
/**
 * ProductServiceImpl 业务组件。
 */
public class ProductServiceImpl implements ProductService {
    /**
     * Day18 P6-S3 缓存键设计说明：
     * 1) 详情键按 productId 精确定位，适合点删；
     * 2) 列表键带 version + queryHash，写链路通过 version 递增实现“整体失效”；
     * 3) suffix(v1) 预留结构升级位，后续可通过升版本平滑换格式。
     */
    private static final String MARKET_PRODUCT_DETAIL_KEY_PREFIX = "cache:product:detail:";
    private static final String MARKET_PRODUCT_LIST_KEY_PREFIX = "cache:product:list:";
    private static final String MARKET_PRODUCT_LIST_VERSION_KEY = "cache:product:list:version";
    private static final String CACHE_KEY_VERSION_SUFFIX = ":v1";
    /** 缓存空值占位符：用于“对象不存在”的短期缓存，防止穿透。 */
    private static final String CACHE_NULL_MARKER = "__NULL__";
    /**
     * 兼容旧管理端入口（/update-status）触发强制下架时的默认原因。
     * 说明：
     * 1) reasonCode 用于审计/事件做结构化检索；
     * 2) reasonText 用于人类可读排障；
     * 3) 固定值能保证同类入口口径一致，避免后续统计口径分叉。
     */
    private static final String ADMIN_COMPAT_REASON_CODE_MANUAL_UPDATE = "manual_update";
    private static final String ADMIN_COMPAT_REASON_TEXT_MANUAL_UPDATE = "管理员通过兼容入口触发下架";
    /**
     * Redis 安全解锁脚本（compare-and-delete）。
     *
     * 背景：
     * 1) 旧实现是“固定值上锁 + 直接 delete 解锁”；
     * 2) 当线程 A 的锁已过期、线程 B 拿到新锁后，A finally 里的 delete 可能误删 B 的锁；
     * 3) 误删后会放大并发回源，导致“锁形同虚设”。
     *
     * 设计：
     * 1) 上锁时写入随机 token（每个持锁线程唯一）；
     * 2) 解锁时仅在“当前 key 的值 == 自己 token”时删除；
     * 3) 判断与删除在 Redis 内同一条 Lua 中执行，保证原子性。
     */
    private static final DefaultRedisScript<Long> SAFE_UNLOCK_SCRIPT = buildSafeUnlockScript();
    private static final TypeReference<PageResult<MarketProductSummaryDTO>> MARKET_LIST_PAGE_TYPE =
            new TypeReference<PageResult<MarketProductSummaryDTO>>() {
            };

    /**
     * 构造安全解锁脚本。
     *
     * 返回值约定：
     * - 1：成功删除（当前线程确实持有该锁）；
     * - 0：未删除（锁已过期 / 被他人重建 / token 不匹配）。
     */
    private static DefaultRedisScript<Long> buildSafeUnlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        return script;
    }

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

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** 总开关：关闭后所有商品读缓存都退化为 DB 直读。 */
    @Value("${demo.cache.enabled:true}")
    private boolean cacheEnabled;

    /** 商品详情缓存开关。 */
    @Value("${demo.cache.product.detail.enabled:true}")
    private boolean productDetailCacheEnabled;

    /** 详情缓存 TTL（秒）。 */
    @Value("${demo.cache.product.detail.ttl-seconds:120}")
    private int productDetailTtlSeconds;

    /** 详情空值缓存 TTL（秒），建议短于正常 TTL。 */
    @Value("${demo.cache.product.detail.null-ttl-seconds:20}")
    private int productDetailNullTtlSeconds;

    /** 详情重建锁 TTL（秒），用于热点过期时防击穿。 */
    @Value("${demo.cache.product.detail.lock-seconds:3}")
    private int productDetailLockSeconds;

    /** 商品列表缓存开关。 */
    @Value("${demo.cache.product.list.enabled:true}")
    private boolean productListCacheEnabled;

    /** 列表缓存 TTL（秒）。 */
    @Value("${demo.cache.product.list.ttl-seconds:45}")
    private int productListTtlSeconds;

    /** 列表重建锁 TTL（秒）。 */
    @Value("${demo.cache.product.list.lock-seconds:3}")
    private int productListLockSeconds;

    /** TTL 抖动百分比，避免大量 key 同时过期导致雪崩。 */
    @Value("${demo.cache.product.jitter-percent:20}")
    private int productCacheTtlJitterPercent;

    @Override
    // Read-only transaction: keeps routing/consistency semantics while reducing write-transaction overhead.
    @Transactional(readOnly = true)
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
                markMarketProductReadCachesDirty(result.getProduct().getId());
            }
            return result.isIdempotent() ? result.getMessage() : "商品审核通过";
        }

        // Day18 P3-S2：驳回原因会进入审计日志与通知文案，统一按纯文本口径收口。
        String r = InputSecurityGuard.normalizePlainText(reason, "驳回原因", 200, true);

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
            markMarketProductReadCachesDirty(result.getProduct().getId());
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
        // Day18 P3-S2：管理员输入参数统一走纯文本守卫，避免非法片段进入审计/消息链路。
        String reasonCode = InputSecurityGuard.normalizePlainText(request.getReasonCode(), "reasonCode", 64, true);
        String reasonText = InputSecurityGuard.normalizePlainText(request.getReasonText(), "reasonText", 255, true);
        String reportTicketNo = InputSecurityGuard.normalizePlainText(request.getReportTicketNo(), "reportTicketNo", 32, false);

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
            markMarketProductReadCachesDirty(result.getProduct().getId());
        }

        return result.isIdempotent() ? result.getMessage() : "强制下架成功";
    }



    /**
     * 查询商品的违规记录。
     */
    @Override
    // Pure query path; readOnly helps prevent accidental flush/write participation.
    @Transactional(readOnly = true)
    public PageResult<ProductViolation> getProductViolations(Long productId, Integer page, Integer pageSize) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safePageSize = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);
        int offset = (safePage - 1) * safePageSize;

        long total = productViolationMapper.countByProductId(productId);
        if (total <= 0) {
            return new PageResult<>(Collections.emptyList(), 0L, safePage, safePageSize);
        }
        List<ProductViolation> rows = productViolationMapper.findByProductIdPage(productId, offset, safePageSize);
        return new PageResult<>(rows, total, safePage, safePageSize);
    }

    /**
     * 添加商品违规记录
     */
    @Override
    public void addProductViolation(ProductViolation violation) {
        productViolationMapper.insert(violation);
    }

    /**
     * 管理端“update-status 兼容入口”状态分发器（Day18 修正）。
     *
     * 背景问题（旧实现）：
     * 1) 直接 setStatus + update，会绕过 transit 统一迁移内核；
     * 2) 审计、事件、缓存失效容易与主链路分叉（同样状态变更，不同副作用）。
     *
     * 修正策略：
     * 1) 保留旧接口地址兼容前端，但内部不再直接改库；
     * 2) 按“目标状态”分发到已有动作方法，复用统一迁移内核：
     *    - on_sale   -> approveProduct（审核通过语义）
     *    - off_shelf -> forceOffShelfProduct（强制下架语义）
     * 3) 对 under_review/sold 直接拒绝，避免破坏状态机语义：
     *    - under_review 应由卖家提审/撤回链路驱动；
     *    - sold 应由成交链路驱动，而不是后台手工改值。
     *
     * 返回值说明：
     * - 透传被分发动作的业务文案，包含幂等命中场景（如“已处理”“商品已下架”）。
     */
    @Override
    public String updateProductStatus(Long productId, String statusDbValue) {
        // Controller 已做 normalize；这里再次做严格枚举校验，防止被其他调用方绕过。
        ProductStatus targetStatus = ProductStatus.fromDbValue(statusDbValue);

        switch (targetStatus) {
            case ON_SHELF:
                // 目标是“上架(on_sale)”时，语义应等价于“管理员审核通过”。
                // 好处：自动复用审核链路里的状态校验、幂等、审计、事件与缓存处理。
                return approveProduct(productId, true, null);
            case OFF_SHELF:
                // 目标是“下架(off_shelf)”时，语义应等价于“管理员强制下架”。
                // 注意：不要直接改 status，否则会丢失强制下架链路的审计与治理事件。
                Long operatorId = BaseContext.getCurrentId();
                if (operatorId == null) {
                    throw new BusinessException(ProductMessageConstant.PRODUCT_NO_PERMISSION_OPERATE);
                }
                return forceOffShelfProduct(operatorId, productId, buildManualUpdateForceOffShelfRequest());
            case UNDER_REVIEW:
                throw new BusinessException("不支持通过该入口直接改为审核中，请走卖家提审/撤回流程");
            case SOLD:
                throw new BusinessException("不支持通过该入口直接改为已售，已售状态应由成交链路驱动");
            default:
                // 理论上 fromDbValue 已经兜底，这里保留 default 仅用于 switch 完整性保护。
                throw new BusinessException("非法商品状态: " + statusDbValue);
        }
    }

    /**
     * 兼容入口触发“强制下架”时的默认参数。
     * 设计目的：
     * 1) 让旧入口也能完整走强制下架主链路（校验 + 并发 + 审计 + 事件 + 缓存）；
     * 2) 固定 reasonCode，后续审计检索“哪些是兼容入口触发”会更直接。
     */
    private ForceOffShelfRequest buildManualUpdateForceOffShelfRequest() {
        ForceOffShelfRequest request = new ForceOffShelfRequest();
        request.setReasonCode(ADMIN_COMPAT_REASON_CODE_MANUAL_UPDATE);
        request.setReasonText(ADMIN_COMPAT_REASON_TEXT_MANUAL_UPDATE);
        return request;
    }

    /**
     * 查询用户自己的商品列表。
     */
    @Override
    // User product list is read-only; keep transaction lightweight for high-frequency list requests.
    @Transactional(readOnly = true)
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
    // Detail lookup has no mutation; mark readOnly to reduce unnecessary transaction cost.
    @Transactional(readOnly = true)
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
        // Day18 P3-S2：商品标题/描述是核心展示字段，先做统一输入安全守卫。
        String safeTitle = InputSecurityGuard.normalizePlainText(request.getTitle(), "商品标题", 120, true);
        String safeDescription = InputSecurityGuard.normalizePlainText(request.getDescription(), "商品描述", 2000, false);

        // Day13 Step6 - 敏感词检测（编辑时）
        String checkText = (safeTitle != null ? safeTitle : "") + " " +
                (safeDescription != null ? safeDescription : "");
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
                // 图片 URL 同样按纯文本规则校验，避免事件属性注入或非法片段进入存储。
                List<String> cleaned = request.getImages().stream()
                        .filter(Objects::nonNull)
                        .map(s -> InputSecurityGuard.normalizePlainText(s, "商品图片URL", 500, false))
                        .filter(Objects::nonNull)
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
                        safeTitle,
                        safeDescription,
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
        markMarketProductReadCachesDirty(result.getProduct().getId());
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
        if (!result.isIdempotent()) {
            markMarketProductReadCachesDirty(result.getProduct().getId());
        }
        return result.isIdempotent() ? result.getMessage() : "下架成功";
    }

    /**
     * 创建或新增相关数据。
     */
    @Override
    public ProductDetailDTO createProduct(Long currentUserId, ProductCreateRequest request) {
        // Day18 P3-S2：创建链路与编辑链路保持同口径的输入安全校验。
        String safeTitle = InputSecurityGuard.normalizePlainText(request.getTitle(), "商品标题", 120, true);
        String safeDescription = InputSecurityGuard.normalizePlainText(request.getDescription(), "商品描述", 2000, false);
        String safeCategory = InputSecurityGuard.normalizePlainText(request.getCategory(), "商品分类", 60, false);

        // Day13 Step6 - 敏感词检测（创建时）
        String checkText = (safeTitle != null ? safeTitle : "") + " " +
                (safeDescription != null ? safeDescription : "");
        if (sensitiveWordService.containsSensitiveWord(checkText)) {
            String matched = sensitiveWordService.getMatchedWords(checkText);
            log.warn("商品创建包含敏感词：userId={}, words={}", currentUserId, matched);
            // 高风险：阻断发布
            throw new BusinessException(ProductMessageConstant.PRODUCT_CONTENT_SENSITIVE_CREATE + matched);
        }

        Product product = new Product();
        product.setOwnerId(currentUserId);
        product.setTitle(safeTitle);
        product.setDescription(safeDescription);
        product.setPrice(request.getPrice());
        product.setCategory(safeCategory);

        // 2) images: List<String> -> "a,b,c"
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            // 多图场景逐个规范化，保证 join 之后的 images 字段可控。
            List<String> safeImages = request.getImages().stream()
                    .filter(Objects::nonNull)
                    .map(s -> InputSecurityGuard.normalizePlainText(s, "商品图片URL", 500, false))
                    .filter(Objects::nonNull)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            product.setImages(safeImages.isEmpty() ? null : String.join(",", safeImages));
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
     * 市场商品列表（用户侧）。
     *
     * 缓存读流程：
     * 1) 先按 version + queryHash 读取列表缓存；
     * 2) 命中直接返回，miss 进入重建；
     * 3) 仅“拿到重建锁”的线程回源 DB 并回填缓存；
     * 4) 未拿到锁的线程短暂等待后重读一次缓存，避免并发回源放大。
     *
     * 注意：
     * - Redis 故障时走 fail-open（直接 DB 查询），不影响主流程可用性；
     * - 列表失效不做全量扫 key，依赖 version 递增实现批量淘汰。
     */
    @Override
    // Market list is a read path (cache + DB fallback); readOnly avoids full write-transaction overhead.
    @Transactional(readOnly = true)
    public PageResult<MarketProductSummaryDTO> getMarketProductList(MarketProductQueryDTO queryDTO) {
        if (!isMarketProductListCacheEnabled()) {
            return loadMarketProductListFromDb(queryDTO);
        }

        String listVersion = getMarketProductListCacheVersion();
        String cacheKey = buildMarketProductListCacheKey(queryDTO, listVersion);
        String cacheJson = safeGetCache(cacheKey);
        if (cacheJson != null) {
            try {
                return objectMapper.readValue(cacheJson, MARKET_LIST_PAGE_TYPE);
            } catch (Exception ex) {
                // 反序列化异常视为脏缓存，删除后回源重建。
                log.warn("商品列表缓存反序列化失败，回源重建: key={}, err={}", cacheKey, ex.getMessage());
                safeDeleteCache(cacheKey);
            }
        }

        String lockKey = cacheKey + ":lock";
        // 注意：这里拿到的不是 boolean，而是“锁凭证 token”。
        // 只有持有同一个 token 的线程，才有资格在 finally 里释放该锁。
        String lockToken = tryAcquireRebuildLock(lockKey, productListLockSeconds);
        if (lockToken != null) {
            try {
                PageResult<MarketProductSummaryDTO> fresh = loadMarketProductListFromDb(queryDTO);
                safeSetCache(cacheKey, toJsonQuietly(fresh), withTtlJitterSeconds(productListTtlSeconds));
                return fresh;
            } finally {
                // 传 token 做 compare-and-delete，避免误删并发线程新拿到的锁。
                safeReleaseRebuildLock(lockKey, lockToken);
            }
        }

        // 未拿到锁，短暂等待后再读一次缓存，避免并发同时回源。
        sleepQuietly(60);
        String retriedCacheJson = safeGetCache(cacheKey);
        if (retriedCacheJson != null) {
            try {
                return objectMapper.readValue(retriedCacheJson, MARKET_LIST_PAGE_TYPE);
            } catch (Exception ex) {
                log.warn("商品列表缓存二次读取反序列化失败，直接回源: key={}, err={}", cacheKey, ex.getMessage());
                safeDeleteCache(cacheKey);
            }
        }

        return loadMarketProductListFromDb(queryDTO);
    }

    /**
     * 统一封装商品列表 DB 查询，便于复用到缓存 miss 场景。
     */
    private PageResult<MarketProductSummaryDTO> loadMarketProductListFromDb(MarketProductQueryDTO queryDTO) {
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
     * 市场商品详情（用户侧）。
     *
     * 缓存读流程：
     * 1) 先读详情缓存；
     * 2) 命中空值标记时，直接按“不可用”返回，避免反复打 DB；
     * 3) miss 时通过短锁控制仅一个线程回源重建；
     * 4) 回源查不到时写入空值短缓存（防穿透）。
     */
    @Override
    // Market detail is also read-only (cache + DB fallback), so use readOnly transaction semantics.
    @Transactional(readOnly = true)
    public MarketProductDetailDTO getMarketProductDetail(Long productId) {
        if (productId == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_ID_REQUIRED);
        }

        if (!isMarketProductDetailCacheEnabled()) {
            return loadMarketProductDetailFromDb(productId);
        }

        String cacheKey = buildMarketProductDetailCacheKey(productId);
        String cacheJson = safeGetCache(cacheKey);
        if (cacheJson != null) {
            if (CACHE_NULL_MARKER.equals(cacheJson)) {
                throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
            }
            try {
                return objectMapper.readValue(cacheJson, MarketProductDetailDTO.class);
            } catch (Exception ex) {
                log.warn("商品详情缓存反序列化失败，回源重建: key={}, err={}", cacheKey, ex.getMessage());
                safeDeleteCache(cacheKey);
            }
        }

        String lockKey = cacheKey + ":lock";
        // 同列表缓存：必须持有 token 才能安全释放锁。
        String lockToken = tryAcquireRebuildLock(lockKey, productDetailLockSeconds);
        if (lockToken != null) {
            try {
                Product product = productMapper.getMarketProductById(productId);
                if (product == null) {
                    safeSetCache(cacheKey, CACHE_NULL_MARKER, normalizeTtlSeconds(productDetailNullTtlSeconds));
                    throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
                }

                MarketProductDetailDTO fresh = toMarketProductDetailDTO(product);
                safeSetCache(cacheKey, toJsonQuietly(fresh), withTtlJitterSeconds(productDetailTtlSeconds));
                return fresh;
            } finally {
                // compare-and-delete，规避“锁过期后误删他人锁”。
                safeReleaseRebuildLock(lockKey, lockToken);
            }
        }

        sleepQuietly(60);
        String retriedCacheJson = safeGetCache(cacheKey);
        if (retriedCacheJson != null) {
            if (CACHE_NULL_MARKER.equals(retriedCacheJson)) {
                throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
            }
            try {
                return objectMapper.readValue(retriedCacheJson, MarketProductDetailDTO.class);
            } catch (Exception ex) {
                log.warn("商品详情缓存二次读取反序列化失败，直接回源: key={}, err={}", cacheKey, ex.getMessage());
                safeDeleteCache(cacheKey);
            }
        }

        return loadMarketProductDetailFromDb(productId);
    }

    /**
     * 统一封装商品详情 DB 查询，便于复用到缓存 miss 场景。
     */
    private MarketProductDetailDTO loadMarketProductDetailFromDb(Long productId) {
        Product product = productMapper.getMarketProductById(productId);
        if (product == null) {
            throw new BusinessException(ProductMessageConstant.PRODUCT_NOT_FOUND_OR_UNAVAILABLE);
        }
        return toMarketProductDetailDTO(product);
    }

    private MarketProductDetailDTO toMarketProductDetailDTO(Product product) {
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
        markMarketProductReadCachesDirty(productId);
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
        markMarketProductReadCachesDirty(result.getProduct().getId());
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
        markMarketProductReadCachesDirty(result.getProduct().getId());
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
        markMarketProductReadCachesDirty(result.getProduct().getId());
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

    /**
     * 商品读缓存统一失效入口。
     *
     * 规则：
     * 1) 详情缓存按 productId 精确删除；
     * 2) 列表缓存使用 version 键递增触发批量失效；
     * 3) 默认在事务提交后执行，避免“回滚但缓存已删”的时序问题。
     */
    private void markMarketProductReadCachesDirty(Long productId) {
        // 统一放在 afterCommit 执行，保证“提交成功才删缓存”。
        runAfterCommitOrNow(() -> {
            if (productId != null) {
                safeDeleteCache(buildMarketProductDetailCacheKey(productId));
            }
            if (isMarketProductListCacheEnabled()) {
                // version 递增后，新请求会自然落到新 key，旧 key 由 TTL 自然淘汰。
                safeIncrement(MARKET_PRODUCT_LIST_VERSION_KEY);
            }
        });
    }

    /**
     * 事务后置执行器：
     * - 有事务：注册 afterCommit；
     * - 无事务：立即执行（例如非事务读改场景）。
     */
    private void runAfterCommitOrNow(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private boolean isMarketProductDetailCacheEnabled() {
        return cacheEnabled && productDetailCacheEnabled && stringRedisTemplate != null;
    }

    private boolean isMarketProductListCacheEnabled() {
        return cacheEnabled && productListCacheEnabled && stringRedisTemplate != null;
    }

    private String buildMarketProductDetailCacheKey(Long productId) {
        return MARKET_PRODUCT_DETAIL_KEY_PREFIX + productId + CACHE_KEY_VERSION_SUFFIX;
    }

    private String getMarketProductListCacheVersion() {
        String version = safeGetCache(MARKET_PRODUCT_LIST_VERSION_KEY);
        return (version == null || version.trim().isEmpty()) ? "0" : version.trim();
    }

    private String buildMarketProductListCacheKey(MarketProductQueryDTO queryDTO, String version) {
        String querySignature = buildMarketProductListQuerySignature(queryDTO);
        return MARKET_PRODUCT_LIST_KEY_PREFIX + version + ":" + querySignature + CACHE_KEY_VERSION_SUFFIX;
    }

    /**
     * 查询参数签名统一口径，避免同义参数生成不同 key。
     */
    private String buildMarketProductListQuerySignature(MarketProductQueryDTO queryDTO) {
        String keyword = normalizeQueryField(queryDTO.getKeyword());
        String category = normalizeQueryField(queryDTO.getCategory());
        Integer page = queryDTO.getPage() == null ? 1 : queryDTO.getPage();
        Integer pageSize = queryDTO.getPageSize() == null ? 10 : queryDTO.getPageSize();
        // 只纳入会影响结果集的数据字段，避免无关参数导致缓存碎片化。
        String normalized = "keyword=" + keyword + "&category=" + category + "&page=" + page + "&pageSize=" + pageSize;
        return sha256Hex(normalized);
    }

    private String normalizeQueryField(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // JDK 常规环境不会进入该分支，兜底避免影响主流程。
            return Integer.toHexString(source.hashCode());
        }
    }

    private int withTtlJitterSeconds(int baseTtlSeconds) {
        int safeBase = normalizeTtlSeconds(baseTtlSeconds);
        int jitterPercent = Math.max(0, productCacheTtlJitterPercent);
        if (jitterPercent == 0) {
            return safeBase;
        }
        // 抖动区间：[-range, +range]，避免同批 key 在同一秒到期。
        int jitterRange = Math.max(1, safeBase * jitterPercent / 100);
        int delta = ThreadLocalRandom.current().nextInt(-jitterRange, jitterRange + 1);
        return Math.max(1, safeBase + delta);
    }

    private int normalizeTtlSeconds(int ttlSeconds) {
        return ttlSeconds <= 0 ? 1 : ttlSeconds;
    }

    private String toJsonQuietly(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            log.warn("缓存序列化失败，跳过写缓存: err={}", ex.getMessage());
            return null;
        }
    }

    private void safeSetCache(String key, String value, int ttlSeconds) {
        if (stringRedisTemplate == null || key == null || value == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(normalizeTtlSeconds(ttlSeconds)));
        } catch (Exception ex) {
            // 缓存写失败不打断业务，交给下次读请求回源重建。
            log.warn("缓存写入失败（降级回源）: key={}, err={}", key, ex.getMessage());
        }
    }

    private String safeGetCache(String key) {
        if (stringRedisTemplate == null || key == null) {
            return null;
        }
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            // 读取失败直接回源 DB，保证请求可用性优先。
            log.warn("缓存读取失败（降级回源）: key={}, err={}", key, ex.getMessage());
            return null;
        }
    }

    private void safeDeleteCache(String key) {
        if (stringRedisTemplate == null || key == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("缓存删除失败: key={}, err={}", key, ex.getMessage());
        }
    }

    private void safeIncrement(String key) {
        if (stringRedisTemplate == null || key == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().increment(key);
        } catch (Exception ex) {
            log.warn("缓存版本递增失败: key={}, err={}", key, ex.getMessage());
        }
    }

    /**
     * 尝试获取缓存重建锁。
     *
     * @return
     * - 非 null：获取成功，返回本线程持有的 lockToken；
     * - null：获取失败（未拿到锁或 Redis 异常）。
     */
    private String tryAcquireRebuildLock(String lockKey, int lockSeconds) {
        if (stringRedisTemplate == null || lockKey == null) {
            return null;
        }
        try {
            // 关键点：每次上锁都生成唯一 token，避免不同线程共享同一锁值。
            String lockToken = UUID.randomUUID().toString();
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    lockKey,
                    lockToken,
                    Duration.ofSeconds(normalizeTtlSeconds(lockSeconds))
            );
            // setIfAbsent 成功表示当前线程获得“唯一重建资格”并持有唯一 token。
            return Boolean.TRUE.equals(locked) ? lockToken : null;
        } catch (Exception ex) {
            log.warn("缓存重建锁获取失败: key={}, err={}", lockKey, ex.getMessage());
            return null;
        }
    }

    /**
     * 安全释放缓存重建锁（compare-and-delete）。
     *
     * 说明：
     * 1) 仅当 key 当前值仍等于 lockToken 才会删除；
     * 2) 不做兜底 delete，宁可“删不掉自己的过期锁”，也不能误删别人新锁；
     * 3) 返回 0 常见于锁已过期或已被其他线程覆盖，属于预期并发现象。
     */
    private void safeReleaseRebuildLock(String lockKey, String lockToken) {
        if (stringRedisTemplate == null || lockKey == null || lockToken == null) {
            return;
        }
        try {
            Long unlocked = stringRedisTemplate.execute(
                    SAFE_UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey),
                    lockToken
            );
            if (unlocked != null && unlocked == 0L) {
                log.debug("缓存重建锁未释放（token 不匹配或已过期）: key={}", lockKey);
            }
        } catch (Exception ex) {
            // 释放失败不做兜底 delete，避免误删后续线程持有的新锁。
            log.warn("缓存重建锁释放失败: key={}, err={}", lockKey, ex.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

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



