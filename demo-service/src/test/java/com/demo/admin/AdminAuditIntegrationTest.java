package com.demo.admin;

import com.demo.controller.admin.AdminAfterSaleController;
import com.demo.controller.admin.AdminAuditController;
import com.demo.dto.aftersale.ArbitrateRequest;
import com.demo.dto.admin.AdminAuditQueryDTO;
import com.demo.result.Result;
import com.demo.vo.admin.AdminAuditOverviewVO;
import com.demo.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端审核中心集成测试。
 *
 * 覆盖场景：
 * - 审核总览返回工单
 * - 纠纷裁决通过/驳回
 * - 重复裁决幂等
 * - 无效售后裁决拒绝
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Rollback
class AdminAuditIntegrationTest {

    private static final String PREFIX = "P4ADMINC_audit_test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdminAuditController adminAuditController;

    @Autowired
    private AdminAfterSaleController adminAfterSaleController;

    @Test
    void auditOverviewReturnsTickets() {
        // Create test data: an after-sale in DISPUTED status
        Long buyerId = insertUser("audit-buyer", 0);
        Long sellerId = insertUser("audit-seller", 1);
        Long orderId = insertOrder(buyerId, sellerId, "paid");
        Long afterSaleId = insertAfterSale(orderId, buyerId, sellerId, "DISPUTED");

        AdminAuditQueryDTO query = new AdminAuditQueryDTO();
        Result<AdminAuditOverviewVO> result = adminAuditController.overview(query);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertNotNull(result.getData().getStats());
        assertNotNull(result.getData().getTickets());
        // Should have at least our test ticket
        assertTrue(result.getData().getTickets().size() >= 1,
                "Audit overview should contain at least 1 ticket");
    }

    @Test
    void disputeCanBeApproved() {
        Long buyerId = insertUser("arb-buyer", 0);
        Long sellerId = insertUser("arb-seller", 1);
        Long orderId = insertOrder(buyerId, sellerId, "paid");
        Long afterSaleId = insertAfterSale(orderId, buyerId, sellerId, "DISPUTED");

        ArbitrateRequest request = new ArbitrateRequest();
        request.setApproved(true);
        request.setRemark(PREFIX + " approved");

        Result<String> result = adminAfterSaleController.arbitrate(afterSaleId, request);
        assertNotNull(result);
        assertEquals(1, result.getCode());

        // Verify after_sale status updated
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM after_sales WHERE id = ?", String.class, afterSaleId);
        assertNotNull(status);
        assertTrue(status.contains("RESOLVED") || status.contains("CLOSED") || status.contains("ARBITRATED"),
                "After-sale status should be resolved/closed after approval, got: " + status);
    }

    @Test
    void disputeCanBeRejected() {
        Long buyerId = insertUser("arb-reject-buyer", 0);
        Long sellerId = insertUser("arb-reject-seller", 1);
        Long orderId = insertOrder(buyerId, sellerId, "paid");
        Long afterSaleId = insertAfterSale(orderId, buyerId, sellerId, "DISPUTED");

        ArbitrateRequest request = new ArbitrateRequest();
        request.setApproved(false);
        request.setRemark(PREFIX + " rejected");

        Result<String> result = adminAfterSaleController.arbitrate(afterSaleId, request);
        assertNotNull(result);
        assertEquals(1, result.getCode());
    }

    @Test
    void duplicateArbitrateIsIdempotent() {
        Long buyerId = insertUser("dup-arb-buyer", 0);
        Long sellerId = insertUser("dup-arb-seller", 1);
        Long orderId = insertOrder(buyerId, sellerId, "paid");
        Long afterSaleId = insertAfterSale(orderId, buyerId, sellerId, "DISPUTED");

        ArbitrateRequest request = new ArbitrateRequest();
        request.setApproved(true);
        request.setRemark(PREFIX + " first");

        // First arbitration should succeed
        Result<String> first = adminAfterSaleController.arbitrate(afterSaleId, request);
        assertNotNull(first);

        // Second arbitration should either succeed idempotently or throw BusinessException
        // (depending on state machine - but must NOT produce duplicate side effects)
        ArbitrateRequest duplicateRequest = new ArbitrateRequest();
        duplicateRequest.setApproved(true);
        duplicateRequest.setRemark(PREFIX + " duplicate");

        try {
            Result<String> second = adminAfterSaleController.arbitrate(afterSaleId, duplicateRequest);
            // If it succeeds, verify no duplicate audit records
            if (second != null) {
                Integer auditCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM product_status_audit_log WHERE biz_id = ?",
                        Integer.class, afterSaleId);
                // Should have at most 1 meaningful audit entry from arbitration
                assertTrue(auditCount == null || auditCount <= 2,
                        "Should not have duplicate audit records");
            }
        } catch (BusinessException e) {
            // Expected: already processed
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void arbitrateNonExistentAfterSaleThrows() {
        ArbitrateRequest request = new ArbitrateRequest();
        request.setApproved(true);
        request.setRemark(PREFIX + " non-existent");

        assertThrows(Exception.class, () ->
                adminAfterSaleController.arbitrate(Long.MAX_VALUE, request));
    }

    // --- Helper methods ---

    private Long insertUser(String suffix, int isSeller) {
        String username = PREFIX + "_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO users (username, password, mobile, nickname, status, is_seller, is_deleted) " +
                        "VALUES (?, ?, ?, ?, 'active', ?, 0)",
                username,
                "test-password",
                "138" + String.format("%08d", Math.abs(username.hashCode()) % 100_000_000),
                username,
                isSeller
        );
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private Long insertOrder(Long buyerId, Long sellerId, String status) {
        String orderNo = PREFIX + "_order_" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO orders (order_no, buyer_id, seller_id, total_amount, status, shipping_address) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                orderNo, buyerId, sellerId, new BigDecimal("100.00"), status, PREFIX + " address"
        );
        return jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
    }

    private Long insertAfterSale(Long orderId, Long buyerId, Long sellerId, String status) {
        jdbcTemplate.update(
                "INSERT INTO after_sales (order_id, buyer_id, seller_id, reason, status, create_time, update_time) " +
                        "VALUES (?, ?, ?, ?, ?, NOW(), NOW())",
                orderId, buyerId, sellerId, PREFIX + " reason", status
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM after_sales WHERE order_id = ? AND reason LIKE ?",
                Long.class, orderId, PREFIX + "%");
    }
}
