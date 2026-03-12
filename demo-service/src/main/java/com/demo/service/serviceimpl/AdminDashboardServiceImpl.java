package com.demo.service.serviceimpl;

import com.demo.dto.Violation.ViolationStatisticsResponseDTO;
import com.demo.dto.user.ProductDTO;
import com.demo.entity.AfterSale;
import com.demo.entity.User;
import com.demo.mapper.AfterSaleMapper;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.AdminDashboardService;
import com.demo.service.ProductService;
import com.demo.service.StatisticsService;
import com.demo.service.ViolationService;
import com.demo.vo.admin.AdminDashboardOverviewVO;
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

/**
 * 管理后台首页聚合服务实现。
 *
 * 说明：
 * 1. 当前项目里已有统计、商品审核、违规治理、售后等独立接口；
 * 2. 这里不是重复造轮子，而是把这些已有能力重新拼成“首页工作台数据包”；
 * 3. 这样前端首页以后只需要请求一次，就能拿到核心指标、审核队列、纠纷队列、风控提醒。
 */
@Service
@Slf4j
public class AdminDashboardServiceImpl implements AdminDashboardService {

    /**
     * 统计服务：提供 GMV、订单量、商品发布量等统计能力。
     */
    @Autowired
    private StatisticsService statisticsService;

    /**
     * 商品服务：提供待审核商品分页列表。
     */
    @Autowired
    private ProductService productService;

    /**
     * 违规服务：提供违规类型统计。
     */
    @Autowired
    private ViolationService violationService;

    /**
     * 售后 Mapper：当前用于补充首页纠纷队列与纠纷数量。
     */
    @Autowired
    private AfterSaleMapper afterSaleMapper;

    /**
     * 用户 Mapper：用于把买家/卖家 ID 转成更可读的用户名称。
     */
    @Autowired
    private UserMapper userMapper;

    /**
     * Dashboard 首页总览查询。
     *
     * @param date 统计日期
     * @return 首页聚合数据
     */
    @Override
    public AdminDashboardOverviewVO getOverview(LocalDate date) {
        // 统一兜底，避免外部传 null 时首页直接报错。
        LocalDate targetDate = date != null ? date : LocalDate.now();

        // 查询订单量与 GMV。
        Map<String, Object> orderStats = statisticsService.countOrderAndGMV(targetDate);

        // 查询商品发布量。
        Map<String, Object> productPublishStats = statisticsService.countProductPublish(targetDate);

        // 查询待审核商品分页列表：首页只展示前 4 条即可。
        PageResult<ProductDTO> pendingPage = productService.getPendingApprovalProducts(1, 4, null, null, null);

        // 查询违规统计，用于首页风控预警。
        ViolationStatisticsResponseDTO violationStatistics = violationService.getViolationStatistics();

        // 查询处于 DISPUTED 状态的售后单，用作首页纠纷处理队列。
        long disputedTotal = afterSaleMapper.countByStatus("DISPUTED");
        List<AfterSale> disputedList = afterSaleMapper.selectByStatus("DISPUTED", 3);

        // 创建最终返回对象。
        AdminDashboardOverviewVO overview = new AdminDashboardOverviewVO();

        // 组装顶部核心指标。
        overview.setCoreMetrics(buildCoreMetrics(orderStats, productPublishStats, pendingPage, violationStatistics, disputedTotal));

        // 组装待审核商品队列。
        overview.setReviewQueue(buildReviewQueue(pendingPage != null ? pendingPage.getRecords() : Collections.emptyList()));

        // 组装纠纷处理队列。
        overview.setDisputeQueue(buildDisputeQueue(disputedList));

        // 组装风控预警列表。
        overview.setRiskAlerts(buildRiskAlerts(violationStatistics));

        return overview;
    }

    /**
     * 组装首页指标卡。
     */
    private List<AdminDashboardOverviewVO.MetricItem> buildCoreMetrics(Map<String, Object> orderStats,
                                                                       Map<String, Object> productPublishStats,
                                                                       PageResult<ProductDTO> pendingPage,
                                                                       ViolationStatisticsResponseDTO violationStatistics,
                                                                       long disputedTotal) {
        // 读取订单数量。
        long orderCount = asLong(orderStats == null ? null : orderStats.get("orderCount"));

        // 读取 GMV。
        BigDecimal gmv = asBigDecimal(orderStats == null ? null : orderStats.get("gmv"));

        // 读取商品发布量。
        long publishTotal = asLong(productPublishStats == null ? null : productPublishStats.get("total"));

        // 待审核商品总数来自分页 total。
        long pendingTotal = pendingPage == null || pendingPage.getTotal() == null ? 0L : pendingPage.getTotal();

        // 违规统计总量，用于补充“争议与举报”指标。
        long violationTotal = sumViolationCount(violationStatistics);

        AdminDashboardOverviewVO.MetricItem gmvMetric = new AdminDashboardOverviewVO.MetricItem();
        gmvMetric.setTitle("今日成交额 (GMV)");
        gmvMetric.setValue(formatCurrency(gmv));
        gmvMetric.setTrend("--");
        gmvMetric.setIsUp(Boolean.TRUE);
        gmvMetric.setSubtext("今日成交订单 " + orderCount + " 单");

        AdminDashboardOverviewVO.MetricItem orderMetric = new AdminDashboardOverviewVO.MetricItem();
        orderMetric.setTitle("新增付款订单");
        orderMetric.setValue(String.valueOf(orderCount));
        orderMetric.setTrend("--");
        orderMetric.setIsUp(Boolean.TRUE);
        orderMetric.setSubtext("统计日期 " + LocalDate.now());

        AdminDashboardOverviewVO.MetricItem reviewMetric = new AdminDashboardOverviewVO.MetricItem();
        reviewMetric.setTitle("待审异常商品");
        reviewMetric.setValue(String.valueOf(pendingTotal));
        reviewMetric.setTrend("--");
        reviewMetric.setIsUp(Boolean.FALSE);
        reviewMetric.setSubtext("当前待审商品 " + (pendingPage == null || pendingPage.getRecords() == null ? 0 : pendingPage.getRecords().size()) + " 条");

        AdminDashboardOverviewVO.MetricItem disputeMetric = new AdminDashboardOverviewVO.MetricItem();
        disputeMetric.setTitle("售后争议 & 举报");
        disputeMetric.setValue(String.valueOf(disputedTotal + violationTotal));
        disputeMetric.setTrend("--");
        disputeMetric.setIsUp(Boolean.FALSE);
        disputeMetric.setSubtext("商品发布量 " + publishTotal + " 条");

        return List.of(gmvMetric, orderMetric, reviewMetric, disputeMetric);
    }

    /**
     * 组装待审核商品队列。
     */
    private List<AdminDashboardOverviewVO.ReviewQueueItem> buildReviewQueue(List<ProductDTO> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        return products.stream().map(product -> {
            AdminDashboardOverviewVO.ReviewQueueItem item = new AdminDashboardOverviewVO.ReviewQueueItem();
            item.setId("审核-" + product.getProductId());
            item.setItem(product.getProductName());
            // 现有 ProductDTO 还没有卖家昵称，这里先给一个说明性占位，后续若 DTO 补字段可再替换。
            item.setUser("待补充卖家信息");
            item.setType(product.getCategory() == null ? "待分类" : product.getCategory());
            item.setPrice(formatCurrency(product.getPrice()));
            item.setTime(formatRelativeTime(product.getSubmitTime()));
            item.setRisk(inferProductRisk(product));
            return item;
        }).toList();
    }

    /**
     * 组装纠纷处理队列。
     */
    private List<AdminDashboardOverviewVO.DisputeQueueItem> buildDisputeQueue(List<AfterSale> afterSales) {
        if (afterSales == null || afterSales.isEmpty()) {
            return Collections.emptyList();
        }

        return afterSales.stream().map(afterSale -> {
            AdminDashboardOverviewVO.DisputeQueueItem item = new AdminDashboardOverviewVO.DisputeQueueItem();
            item.setId("纠纷-" + afterSale.getId());
            item.setReason(afterSale.getReason() == null ? "售后纠纷待平台处理" : afterSale.getReason());
            item.setTarget("订单 #" + afterSale.getOrderId());
            item.setUser(buildDisputeUserLabel(afterSale));
            item.setLevel(inferDisputeLevel(afterSale));
            return item;
        }).toList();
    }

    /**
     * 组装风控预警列表。
     */
    private List<AdminDashboardOverviewVO.RiskAlertItem> buildRiskAlerts(ViolationStatisticsResponseDTO violationStatistics) {
        if (violationStatistics == null || violationStatistics.getViolationTypeDistribution() == null) {
            return Collections.emptyList();
        }

        return violationStatistics.getViolationTypeDistribution()
                .stream()
                .limit(3)
                .map(distribution -> {
                    AdminDashboardOverviewVO.RiskAlertItem item = new AdminDashboardOverviewVO.RiskAlertItem();
                    item.setId("风控-" + distribution.getViolationType());
                    item.setType(distribution.getViolationTypeDesc() != null
                            ? distribution.getViolationTypeDesc()
                            : distribution.getViolationType());
                    item.setTarget("违规类型: " + distribution.getViolationType());
                    item.setCount(distribution.getCount() + " 次");
                    return item;
                })
                .toList();
    }

    /**
     * 根据商品信息粗略推断风险等级。
     */
    private String inferProductRisk(ProductDTO product) {
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

    /**
     * 构造纠纷展示文案。
     */
    private String buildDisputeUserLabel(AfterSale afterSale) {
        User buyer = afterSale.getBuyerId() == null ? null : userMapper.selectById(afterSale.getBuyerId());
        User seller = afterSale.getSellerId() == null ? null : userMapper.selectById(afterSale.getSellerId());

        String buyerName = resolveUserDisplayName(buyer, afterSale.getBuyerId());
        String sellerName = resolveUserDisplayName(seller, afterSale.getSellerId());
        return "买家 " + buyerName + " vs 卖家 " + sellerName;
    }

    /**
     * 根据售后单停留时长给一个简单优先级。
     */
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

    /**
     * 生成更适合展示的用户名。
     */
    private String resolveUserDisplayName(User user, Long userId) {
        if (user == null) {
            return userId == null ? "未知用户" : "用户#" + userId;
        }
        if (user.getNickname() != null && !user.getNickname().trim().isEmpty()) {
            return user.getNickname().trim();
        }
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            return user.getUsername().trim();
        }
        return userId == null ? "未知用户" : "用户#" + userId;
    }

    /**
     * 汇总违规统计总数。
     */
    private long sumViolationCount(ViolationStatisticsResponseDTO violationStatistics) {
        if (violationStatistics == null || violationStatistics.getViolationTypeDistribution() == null) {
            return 0L;
        }

        return violationStatistics.getViolationTypeDistribution()
                .stream()
                .mapToLong(ViolationStatisticsResponseDTO.ViolationTypeDistribution::getCount)
                .sum();
    }

    /**
     * 安全读取 long 值。
     */
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
            log.warn("Dashboard 聚合转换 long 失败: value={}", value);
            return 0L;
        }
    }

    /**
     * 安全读取 BigDecimal 值。
     */
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
            log.warn("Dashboard 聚合转换 BigDecimal 失败: value={}", value);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 金额格式化，直接按首页展示风格返回字符串。
     */
    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        if (safeAmount.compareTo(new BigDecimal("10000")) >= 0) {
            return "￥ " + safeAmount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP) + "万";
        }
        return "￥ " + safeAmount.stripTrailingZeros().toPlainString();
    }

    /**
     * 时间转成“几分钟前 / 几小时前”这种更适合工作台阅读的格式。
     */
    private String formatRelativeTime(LocalDateTime time) {
        if (time == null) {
            return "刚刚";
        }

        Duration duration = Duration.between(time, LocalDateTime.now());
        long minutes = Math.max(1, duration.toMinutes());
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
