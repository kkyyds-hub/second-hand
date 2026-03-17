package com.demo.service.serviceimpl;

import com.demo.dto.Violation.ViolationStatisticsResponseDTO;
import com.demo.dto.user.ProductDTO;
import com.demo.entity.AfterSale;
import com.demo.entity.Product;
import com.demo.entity.User;
import com.demo.enumeration.ProductStatus;
import com.github.pagehelper.PageHelper;
import com.demo.mapper.AfterSaleMapper;
import com.demo.mapper.ProductMapper;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.AdminDashboardService;
import com.demo.service.ProductService;
import com.demo.service.StatisticsService;
import com.demo.service.ViolationService;
import com.demo.vo.admin.AdminDashboardOverviewVO;
import com.demo.vo.admin.AdminDashboardReviewQueueItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ViolationService violationService;

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public AdminDashboardOverviewVO getOverview(LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now();

        Map<String, Object> orderStats = statisticsService.countOrderAndGMV(targetDate);
        Map<String, Object> productPublishStats = statisticsService.countProductPublish(targetDate);
        PageResult<ProductDTO> pendingPage = productService.getPendingApprovalProducts(1, 4, null, null, null);
        ViolationStatisticsResponseDTO violationStatistics = violationService.getViolationStatistics();
        long disputedTotal = afterSaleMapper.countByStatus("DISPUTED");
        List<AfterSale> disputedList = afterSaleMapper.selectByStatus("DISPUTED", 3);

        AdminDashboardOverviewVO overview = new AdminDashboardOverviewVO();
        overview.setCoreMetrics(buildCoreMetrics(
                targetDate,
                orderStats,
                productPublishStats,
                pendingPage,
                violationStatistics,
                disputedTotal
        ));

        List<Product> pendingProducts = loadDashboardPendingProducts(4);
        overview.setReviewQueue(buildReviewQueue(pendingProducts));
        overview.setDisputeQueue(buildDisputeQueue(disputedList));
        overview.setRiskAlerts(buildRiskAlerts(violationStatistics));
        return overview;
    }

    private List<AdminDashboardOverviewVO.MetricItem> buildCoreMetrics(LocalDate targetDate,
                                                                       Map<String, Object> orderStats,
                                                                       Map<String, Object> productPublishStats,
                                                                       PageResult<ProductDTO> pendingPage,
                                                                       ViolationStatisticsResponseDTO violationStatistics,
                                                                       long disputedTotal) {
        long orderCount = asLong(orderStats == null ? null : orderStats.get("orderCount"));
        BigDecimal gmv = asBigDecimal(orderStats == null ? null : orderStats.get("gmv"));
        long publishTotal = asLong(productPublishStats == null ? null : productPublishStats.get("total"));
        long pendingTotal = pendingPage == null || pendingPage.getTotal() == null ? 0L : pendingPage.getTotal();
        long violationTotal = sumViolationCount(violationStatistics);

        LocalDate previousDate = targetDate.minusDays(1);
        Map<String, Object> previousOrderStats = statisticsService.countOrderAndGMV(previousDate);
        long previousOrderCount = asLong(previousOrderStats == null ? null : previousOrderStats.get("orderCount"));
        BigDecimal previousGmv = asBigDecimal(previousOrderStats == null ? null : previousOrderStats.get("gmv"));

        long pendingTodayNew = productMapper.countByStatusAndDate(ProductStatus.UNDER_REVIEW.getDbValue(), targetDate);
        long pendingYesterdayNew = productMapper.countByStatusAndDate(ProductStatus.UNDER_REVIEW.getDbValue(), previousDate);
        long disputedTodayNew = afterSaleMapper.countByStatusAndDate("DISPUTED", targetDate);
        long disputedYesterdayNew = afterSaleMapper.countByStatusAndDate("DISPUTED", previousDate);

        AdminDashboardOverviewVO.MetricItem gmvMetric = new AdminDashboardOverviewVO.MetricItem();
        gmvMetric.setTitle("今日成交额(GMV)");
        gmvMetric.setValue(formatCurrency(gmv));
        gmvMetric.setTrend(formatTrendPercent(gmv, previousGmv));
        gmvMetric.setIsUp(gmv.compareTo(previousGmv) >= 0);
        gmvMetric.setSubtext("今日成交订单 " + orderCount + " 单");

        AdminDashboardOverviewVO.MetricItem orderMetric = new AdminDashboardOverviewVO.MetricItem();
        orderMetric.setTitle("新增付款订单");
        orderMetric.setValue(String.valueOf(orderCount));
        orderMetric.setTrend(formatTrendPercent(orderCount, previousOrderCount));
        orderMetric.setIsUp(orderCount >= previousOrderCount);
        orderMetric.setSubtext("昨日同期 " + previousOrderCount + " 单");

        AdminDashboardOverviewVO.MetricItem reviewMetric = new AdminDashboardOverviewVO.MetricItem();
        reviewMetric.setTitle("待审异常商品");
        reviewMetric.setValue(String.valueOf(pendingTotal));
        reviewMetric.setTrend(formatTrendPercent(pendingTodayNew, pendingYesterdayNew));
        reviewMetric.setIsUp(pendingTodayNew <= pendingYesterdayNew);
        reviewMetric.setSubtext("今日新增待审 " + pendingTodayNew + " 条，商品发布 " + publishTotal + " 条");

        AdminDashboardOverviewVO.MetricItem disputeMetric = new AdminDashboardOverviewVO.MetricItem();
        disputeMetric.setTitle("售后争议 & 举报");
        disputeMetric.setValue(String.valueOf(disputedTotal));
        disputeMetric.setTrend(formatTrendPercent(disputedTodayNew, disputedYesterdayNew));
        disputeMetric.setIsUp(disputedTodayNew <= disputedYesterdayNew);
        disputeMetric.setSubtext("待处理纠纷 " + disputedTotal + " 单，违规累计 " + violationTotal + " 次");

        return List.of(gmvMetric, orderMetric, reviewMetric, disputeMetric);
    }

    /**
     * Dashboard 审核队列单独直连 Product 实体查询。
     *
     * 2026-03-16 这里不再复用 ProductReview 的 ProductDTO：
     * - ProductDTO 需要兼容 ProductReview 历史运行时字节码差异；
     * - Dashboard 只读概览更关注“卖家展示名 + 商品摘要”的稳定输出；
     * - 因此前端首页的 reviewQueue 改走专用 VO，避免一个 DTO 兼容问题把两条链路同时拖挂。
     */
    private List<AdminDashboardReviewQueueItemVO> buildReviewQueue(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        return products.stream().map(product -> {
            AdminDashboardReviewQueueItemVO item = new AdminDashboardReviewQueueItemVO();
            item.setId("审核-" + product.getId());
            item.setItem(product.getTitle() == null || product.getTitle().isBlank()
                    ? "未命名商品"
                    : product.getTitle());
            item.setSellerName(resolveProductSellerName(product));
            item.setType(product.getCategory() == null || product.getCategory().isBlank()
                    ? "未分类"
                    : product.getCategory());
            item.setPrice(formatCurrency(product.getPrice()));
            item.setTime(formatRelativeTime(product.getCreateTime()));
            item.setRisk(inferProductRisk(product));
            return item;
        }).toList();
    }

    /**
     * 首页 reviewQueue 只取最近 4 条待审核商品。
     *
     * 这里显式分页，而不是把全部待审核商品拉回内存后再截断，
     * 避免 Dashboard 每次打开都把 ProductReview 列表查询成本放大。
     */
    private List<Product> loadDashboardPendingProducts(int limit) {
        int safeLimit = limit <= 0 ? 4 : limit;
        PageHelper.startPage(1, safeLimit, false);
        List<Product> products = productMapper.getPendingApprovalProducts(null, null, null);
        return products == null ? Collections.emptyList() : products;
    }

    private List<AdminDashboardOverviewVO.DisputeQueueItem> buildDisputeQueue(List<AfterSale> afterSales) {
        if (afterSales == null || afterSales.isEmpty()) {
            return Collections.emptyList();
        }

        return afterSales.stream().map(afterSale -> {
            AdminDashboardOverviewVO.DisputeQueueItem item = new AdminDashboardOverviewVO.DisputeQueueItem();
            item.setId("纠纷-" + afterSale.getId());
            item.setReason(afterSale.getReason() == null || afterSale.getReason().isBlank()
                    ? "售后纠纷待处理"
                    : afterSale.getReason());
            item.setTarget(afterSale.getOrderId() == null
                    ? "订单未知"
                    : "订单 #" + afterSale.getOrderId());
            item.setUser(buildDisputeUserLabel(afterSale));
            item.setLevel(inferDisputeLevel(afterSale));
            return item;
        }).toList();
    }

    private List<AdminDashboardOverviewVO.RiskAlertItem> buildRiskAlerts(ViolationStatisticsResponseDTO violationStatistics) {
        if (violationStatistics == null || violationStatistics.getViolationTypeDistribution() == null) {
            return Collections.emptyList();
        }

        return violationStatistics.getViolationTypeDistribution()
                .stream()
                .limit(3)
                .map(distribution -> {
                    String violationType = (distribution.getViolationType() == null || distribution.getViolationType().isBlank())
                            ? "unknown"
                            : distribution.getViolationType();
                    String typeDesc = (distribution.getViolationTypeDesc() == null || distribution.getViolationTypeDesc().isBlank())
                            ? violationType
                            : distribution.getViolationTypeDesc();

                    AdminDashboardOverviewVO.RiskAlertItem item = new AdminDashboardOverviewVO.RiskAlertItem();
                    item.setId("违规-" + violationType);
                    item.setType(typeDesc);
                    item.setTarget("违规类型: " + violationType);
                    item.setCount(distribution.getCount() + " 次");
                    return item;
                })
                .toList();
    }

    /**
     * Dashboard 审核队列展示“卖家”列时，优先回填真实昵称 / 用户名。
     *
     * 如果 ownerId 缺失或用户记录已不存在，仍然回退到稳定文案，
     * 这样首页不会因为脏数据而出现空白列或直接报错。
     */
    private String resolveProductSellerName(Product product) {
        if (product == null || product.getOwnerId() == null) {
            return "未知卖家";
        }

        User owner = userMapper.selectById(product.getOwnerId());
        return resolveUserDisplayName(owner, product.getOwnerId());
    }

    private String inferProductRisk(Product product) {
        if (product == null) {
            return "正常";
        }

        BigDecimal price = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
        if (price.compareTo(new BigDecimal("50000")) >= 0) {
            return "高风险";
        }
        if (price.compareTo(new BigDecimal("10000")) >= 0) {
            return "中风险";
        }
        return "正常";
    }

    private String buildDisputeUserLabel(AfterSale afterSale) {
        if (afterSale == null) {
            return "买家 未知用户 vs 卖家 未知用户";
        }
        User buyer = afterSale.getBuyerId() == null ? null : userMapper.selectById(afterSale.getBuyerId());
        User seller = afterSale.getSellerId() == null ? null : userMapper.selectById(afterSale.getSellerId());
        String buyerName = resolveUserDisplayName(buyer, afterSale.getBuyerId());
        String sellerName = resolveUserDisplayName(seller, afterSale.getSellerId());
        return "买家 " + buyerName + " vs 卖家 " + sellerName;
    }

    private String inferDisputeLevel(AfterSale afterSale) {
        if (afterSale == null || afterSale.getUpdateTime() == null) {
            return "中风险";
        }

        long hours = Duration.between(afterSale.getUpdateTime(), LocalDateTime.now()).toHours();
        if (hours >= 24) {
            return "紧急";
        }
        if (hours >= 6) {
            return "中风险";
        }
        return "待处理";
    }

    private String resolveUserDisplayName(User user, Long userId) {
        if (user == null) {
            return userId == null ? "未知用户" : "用户#" + userId;
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname().trim();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        return userId == null ? "未知用户" : "用户#" + userId;
    }

    private long sumViolationCount(ViolationStatisticsResponseDTO violationStatistics) {
        if (violationStatistics == null || violationStatistics.getViolationTypeDistribution() == null) {
            return 0L;
        }

        return violationStatistics.getViolationTypeDistribution()
                .stream()
                .mapToLong(ViolationStatisticsResponseDTO.ViolationTypeDistribution::getCount)
                .sum();
    }

    private String formatTrendPercent(long current, long previous) {
        return formatTrendPercent(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private String formatTrendPercent(BigDecimal current, BigDecimal previous) {
        BigDecimal safeCurrent = current == null ? BigDecimal.ZERO : current;
        BigDecimal safePrevious = previous == null ? BigDecimal.ZERO : previous;

        if (safePrevious.compareTo(BigDecimal.ZERO) == 0) {
            if (safeCurrent.compareTo(BigDecimal.ZERO) == 0) {
                return "0.0%";
            }
            return safeCurrent.compareTo(BigDecimal.ZERO) > 0 ? "+100.0%" : "-100.0%";
        }

        BigDecimal delta = safeCurrent.subtract(safePrevious);
        BigDecimal percent = delta.multiply(BigDecimal.valueOf(100))
                .divide(safePrevious.abs(), 1, RoundingMode.HALF_UP);
        String sign = percent.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
        return sign + percent.toPlainString() + "%";
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            log.warn("Dashboard cast to long failed, value={}", value);
            return 0L;
        }
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            log.warn("Dashboard cast to BigDecimal failed, value={}", value);
            return BigDecimal.ZERO;
        }
    }

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.abs().compareTo(new BigDecimal("10000")) >= 0) {
            BigDecimal wan = safeAmount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
            return "¥" + wan.stripTrailingZeros().toPlainString() + "万";
        }
        return "¥" + safeAmount.stripTrailingZeros().toPlainString();
    }

    private String formatRelativeTime(LocalDateTime time) {
        if (time == null) {
            return "刚刚";
        }

        Duration duration = Duration.between(time, LocalDateTime.now());
        if (duration.isNegative()) {
            return "刚刚";
        }

        long minutes = duration.toMinutes();
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + "分钟前";
        }

        long hours = duration.toHours();
        if (hours < 24) {
            return hours + "小时前";
        }

        return time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
