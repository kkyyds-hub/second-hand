package com.demo.admin;

import com.demo.context.BaseContext;
import com.demo.controller.admin.AdminOrderController;
import com.demo.dto.admin.AdminOrderDTO;
import com.demo.dto.admin.AdminOrderDetailDTO;
import com.demo.dto.admin.OrderFlagRequest;
import com.demo.dto.admin.AdminOrderQueryDTO;
import com.demo.mapper.OrderMapper;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 管理端订单 Mapper 集成测试（拆分版）。
 *
 * 覆盖：
 * - 列表筛选、排序、关键词搜索
 * - 已删除买家/卖家订单仍可见
 * - 订单标记幂等
 * - 无效标记类型拒绝
 * - 不存在订单拒绝
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Rollback
class AdminOrderMapperIntegrationTest {

    private static final String PREFIX = "P4ADMINC_order_mapper";
    private static final String TEST_ADDRESS = "P4ADMINC integration address";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AdminOrderController adminOrderController;

    // Shared fixture: a normal buyer, a normal seller
    private Long activeBuyer;
    private Long activeSeller;
    private Long order1;
    private Long order2;

    @BeforeEach
    void setUpSharedFixture() {
        activeBuyer = insertUser("active-buyer", 0);
        activeSeller = insertUser("active-seller", 1);
        order1 = insertOrder(activeBuyer, activeSeller, "order-1", "paid");
        order2 = insertOrder(activeBuyer, activeSeller, "order-2", "shipped");
    }

    // ──────────────────────────────────────────────
    // Admin order list filters and sorts
    // ──────────────────────────────────────────────

    @Nested
    class AdminOrderListFiltersAndSorts {

        @Test
        void adminOrderListFiltersAndSorts() {
            Result<PageResult<AdminOrderDTO>> firstPage = adminOrderController.listOrders(
                    1, 20, null, PREFIX, null, null, "createTime", "asc");
            assertEquals(2L, firstPage.getData().getTotal());
            assertEquals(2, firstPage.getData().getRecords().size());

            Result<PageResult<AdminOrderDTO>> paginated = adminOrderController.listOrders(
                    1, 1, null, PREFIX, null, null, "createTime", "asc");
            assertEquals(2L, paginated.getData().getTotal());
            assertEquals(1, paginated.getData().getRecords().size());

            Result<PageResult<AdminOrderDTO>> statusFiltered = adminOrderController.listOrders(
                    1, 20, "shipped", PREFIX, null, null, "createTime", "asc");
            assertEquals(1L, statusFiltered.getData().getTotal());

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Result<PageResult<AdminOrderDTO>> timeFiltered = adminOrderController.listOrders(
                    1, 20, null, PREFIX, "2000-01-01 00:00:00", now, "createTime", "desc");
            assertEquals(2L, timeFiltered.getData().getTotal());

            assertThrows(BusinessException.class, () -> adminOrderController.listOrders(
                    1, 20, null, PREFIX, null, null, "orderNo desc; drop table orders", "sideways"));
        }
    }

    // ──────────────────────────────────────────────
    // Deleted buyer order remains visible
    // ──────────────────────────────────────────────

    @Nested
    class DeletedBuyerOrder {

        @Test
        void deletedBuyerOrderRemainsVisible() {
            Long buyerDeleted = insertUser("buyer-will-delete", 0);
            Long seller = insertUser("seller-for-deleted-buyer", 1);
            Long deletedBuyerOrder = insertOrder(buyerDeleted, seller, "buyer-deleted-order", "paid");

            jdbcTemplate.update("UPDATE users SET is_deleted = 1 WHERE id = ?", buyerDeleted);

            AdminOrderQueryDTO query = new AdminOrderQueryDTO();
            query.setPage(1);
            query.setPageSize(20);
            query.setSortField("createTime");
            query.setSortOrder("asc");
            query.setKeyword(PREFIX + "_buyer-deleted-order");

            List<AdminOrderDTO> orders = orderMapper.listAdminOrders(query);
            assertEquals(1, orders.size());

            AdminOrderDTO order = orders.get(0);
            assertEquals(deletedBuyerOrder, order.getOrderId());
            assertEquals(Boolean.TRUE, order.getBuyerDeleted());
            assertFalse(Boolean.TRUE.equals(order.getSellerDeleted()));

            AdminOrderDetailDTO detail = orderMapper.getAdminOrderDetail(deletedBuyerOrder);
            assertNotNull(detail);
            assertEquals(Boolean.TRUE, detail.getBuyerDeleted());
            assertEquals(buyerDeleted, detail.getBuyerId());
        }
    }

    // ──────────────────────────────────────────────
    // Deleted seller order remains visible
    // ──────────────────────────────────────────────

    @Nested
    class DeletedSellerOrder {

        @Test
        void deletedSellerOrderRemainsVisible() {
            Long buyer = insertUser("buyer-for-deleted-seller", 0);
            Long sellerDeleted = insertUser("seller-will-delete", 1);
            Long deletedSellerOrder = insertOrder(buyer, sellerDeleted, "seller-deleted-order", "shipped");

            jdbcTemplate.update("UPDATE users SET is_deleted = 1 WHERE id = ?", sellerDeleted);

            AdminOrderQueryDTO query = new AdminOrderQueryDTO();
            query.setPage(1);
            query.setPageSize(20);
            query.setSortField("createTime");
            query.setSortOrder("asc");
            query.setKeyword(PREFIX + "_seller-deleted-order");

            List<AdminOrderDTO> orders = orderMapper.listAdminOrders(query);
            assertEquals(1, orders.size());

            AdminOrderDTO order = orders.get(0);
            assertEquals(deletedSellerOrder, order.getOrderId());
            assertEquals(Boolean.TRUE, order.getSellerDeleted());
            assertFalse(Boolean.TRUE.equals(order.getBuyerDeleted()));

            AdminOrderDetailDTO detail = orderMapper.getAdminOrderDetail(deletedSellerOrder);
            assertNotNull(detail);
            assertEquals(Boolean.TRUE, detail.getSellerDeleted());
            assertEquals(sellerDeleted, detail.getSellerId());
        }
    }

    // ──────────────────────────────────────────────
    // Order flag idempotent
    // ──────────────────────────────────────────────

    @Nested
    class OrderFlagIdempotent {

        @Test
        void orderFlagIsIdempotent() {
            OrderFlagRequest validFlag = new OrderFlagRequest();
            validFlag.setType("PAYMENT_RISK");
            validFlag.setRemark("P4ADMINC integration flag");

            BaseContext.setCurrentId(1L);
            try {
                String first = adminOrderController.flagOrder(order1, validFlag).getData();
                assertEquals("标记成功", first);

                String second = adminOrderController.flagOrder(order1, validFlag).getData();
                assertEquals("订单已存在该类型标记", second);
            } finally {
                BaseContext.removeCurrentId();
            }

            assertEquals(1, adminOrderController.listOrderFlags(order1).getData().size());
        }
    }

    // ──────────────────────────────────────────────
    // Invalid order flag rejected
    // ──────────────────────────────────────────────

    @Nested
    class InvalidOrderFlag {

        @Test
        void invalidOrderFlagIsRejected() {
            OrderFlagRequest invalidFlag = new OrderFlagRequest();
            invalidFlag.setType("NOT_A_REAL_FLAG");
            invalidFlag.setRemark("should fail");

            assertThrows(BusinessException.class,
                    () -> adminOrderController.flagOrder(order1, invalidFlag));
        }
    }

    // ──────────────────────────────────────────────
    // Missing order rejected
    // ──────────────────────────────────────────────

    @Nested
    class MissingOrder {

        @Test
        void missingOrderIsRejected() {
            // getOrderDetail with non-existent ID
            assertThrows(BusinessException.class,
                    () -> adminOrderController.getOrderDetail(Long.MAX_VALUE));

            // flagOrder with non-existent ID
            OrderFlagRequest flag = new OrderFlagRequest();
            flag.setType("PAYMENT_RISK");
            flag.setRemark("test");
            BaseContext.setCurrentId(1L);
            try {
                assertThrows(BusinessException.class,
                        () -> adminOrderController.flagOrder(Long.MAX_VALUE, flag));
            } finally {
                BaseContext.removeCurrentId();
            }
        }
    }

    // ──────────────────────────────────────────────
    // Fixture helpers (shared)
    // ──────────────────────────────────────────────

    private Long insertUser(String suffix, int isSeller) {
        String username = PREFIX + "_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO users (username, password, mobile, nickname, status, is_seller, is_deleted) " +
                        "VALUES (?, ?, ?, ?, 'active', ?, 0)",
                username,
                "test-password",
                "139" + String.format("%08d", Math.abs(username.hashCode()) % 100_000_000),
                username,
                isSeller
        );
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private Long insertOrder(Long buyerId, Long sellerId, String suffix, String status) {
        String orderNo = PREFIX + "_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO orders (order_no, buyer_id, seller_id, total_amount, status, shipping_address) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                orderNo, buyerId, sellerId, new BigDecimal("88.00"), status, TEST_ADDRESS
        );
        return jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
    }
}
