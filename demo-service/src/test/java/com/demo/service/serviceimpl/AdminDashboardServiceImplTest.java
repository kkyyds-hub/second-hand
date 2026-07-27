package com.demo.service.serviceimpl;

import com.demo.entity.AfterSale;
import com.demo.entity.Product;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Dashboard 格式化/风险推断方法单元测试。
 *
 * 覆盖：
 * - null createTime → 时间未知
 * - future createTime → 不得返回"刚刚"
 * - null product → 风险未提供
 * - null price → 风险未提供
 * - 低价真实商品 → 普通
 * - 中高价商品 → 中风险/高风险
 * - null dispute updateTime → 风险未提供
 * - 真实纠纷时长 → 待处理/中风险/紧急
 */
class AdminDashboardServiceImplTest {

    /**
     * 被测方法均声明为 package-private，不依赖 @Autowired 字段，
     * 因此可以直接 new 实例测试，无需启动 Spring 上下文。
     */
    private final AdminDashboardServiceImpl service = new AdminDashboardServiceImpl();

    // ──────────────────────────────────────────────
    // formatRelativeTime
    // ──────────────────────────────────────────────

    @Nested
    class FormatRelativeTime {

        @Test
        void nullCreateTimeShouldReturnTimeUnknown() {
            String result = service.formatRelativeTime(null);
            assertEquals("时间未知", result);
        }

        @Test
        void futureCreateTimeShouldNotReturnJustNow() {
            LocalDateTime futureTime = LocalDateTime.now().plusHours(1);
            String result = service.formatRelativeTime(futureTime);
            assertNotEquals("刚刚", result, "Future time must not return '刚刚'");
            assertEquals("时间异常", result);
        }

        @Test
        void recentPastShouldReturnJustNow() {
            LocalDateTime lessThanOneMinuteAgo = LocalDateTime.now().minusSeconds(30);
            String result = service.formatRelativeTime(lessThanOneMinuteAgo);
            assertEquals("刚刚", result);
        }

        @Test
        void minutesAgoShouldReturnMinutesText() {
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            String result = service.formatRelativeTime(fiveMinutesAgo);
            assertEquals("5分钟前", result);
        }

        @Test
        void hoursAgoShouldReturnHoursText() {
            LocalDateTime threeHoursAgo = LocalDateTime.now().minusHours(3);
            String result = service.formatRelativeTime(threeHoursAgo);
            assertEquals("3小时前", result);
        }

        @Test
        void olderShouldReturnDateOnly() {
            LocalDateTime twoDaysAgo = LocalDateTime.now().minusDays(2);
            String result = service.formatRelativeTime(twoDaysAgo);
            // Should format as MM-dd HH:mm, not relative text
            assertNotEquals("刚刚", result);
            assertNotEquals("时间未知", result);
            assertNotEquals("时间异常", result);
        }
    }

    // ──────────────────────────────────────────────
    // inferProductRisk
    // ──────────────────────────────────────────────

    @Nested
    class InferProductRisk {

        @Test
        void nullProductShouldReturnRiskUnavailable() {
            String result = service.inferProductRisk(null);
            assertEquals("风险未提供", result);
        }

        @Test
        void nullPriceShouldReturnRiskUnavailable() {
            Product product = new Product();
            product.setPrice(null);
            String result = service.inferProductRisk(product);
            assertEquals("风险未提供", result);
        }

        @Test
        void lowPriceShouldReturnNormal() {
            Product product = new Product();
            product.setPrice(new BigDecimal("99.99"));
            String result = service.inferProductRisk(product);
            assertEquals("普通", result);
        }

        @Test
        void priceAtThreshold10000ShouldReturnMediumRisk() {
            Product product = new Product();
            product.setPrice(new BigDecimal("10000"));
            String result = service.inferProductRisk(product);
            assertEquals("中风险", result);
        }

        @Test
        void highPriceShouldReturnHighRisk() {
            Product product = new Product();
            product.setPrice(new BigDecimal("50000"));
            String result = service.inferProductRisk(product);
            assertEquals("高风险", result);
        }

        @Test
        void priceBetween10kAnd50kShouldReturnMediumRisk() {
            Product product = new Product();
            product.setPrice(new BigDecimal("25000"));
            String result = service.inferProductRisk(product);
            assertEquals("中风险", result);
        }

        @Test
        void zeroPriceShouldReturnNormal() {
            Product product = new Product();
            product.setPrice(BigDecimal.ZERO);
            String result = service.inferProductRisk(product);
            assertEquals("普通", result);
        }
    }

    // ──────────────────────────────────────────────
    // inferDisputeLevel
    // ──────────────────────────────────────────────

    @Nested
    class InferDisputeLevel {

        @Test
        void nullAfterSaleShouldReturnRiskUnavailable() {
            String result = service.inferDisputeLevel(null);
            assertEquals("风险未提供", result);
        }

        @Test
        void nullUpdateTimeShouldReturnRiskUnavailable() {
            AfterSale afterSale = new AfterSale();
            afterSale.setUpdateTime(null);
            String result = service.inferDisputeLevel(afterSale);
            assertEquals("风险未提供", result);
        }

        @Test
        void recentUpdateShouldReturnPending() {
            AfterSale afterSale = new AfterSale();
            afterSale.setUpdateTime(LocalDateTime.now().minusHours(2));
            String result = service.inferDisputeLevel(afterSale);
            assertEquals("待处理", result);
        }

        @Test
        void midAgedUpdateShouldReturnMediumRisk() {
            AfterSale afterSale = new AfterSale();
            afterSale.setUpdateTime(LocalDateTime.now().minusHours(10));
            String result = service.inferDisputeLevel(afterSale);
            assertEquals("中风险", result);
        }

        @Test
        void oldUpdateShouldReturnUrgent() {
            AfterSale afterSale = new AfterSale();
            afterSale.setUpdateTime(LocalDateTime.now().minusHours(30));
            String result = service.inferDisputeLevel(afterSale);
            assertEquals("紧急", result);
        }
    }
}
