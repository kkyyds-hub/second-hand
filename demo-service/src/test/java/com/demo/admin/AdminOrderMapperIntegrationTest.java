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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Rollback
class AdminOrderMapperIntegrationTest {

    private static final String PREFIX = "P4ADMINB_mapper_integration";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private AdminOrderController adminOrderController;

    @Test
    void retainsOrdersAndIdentifiesDeletedBuyersAndSellers() {
        Long buyerDeleted = insertUser("buyer-deleted", 0);
        Long sellerActive = insertUser("seller-active", 1);
        Long sellerDeleted = insertUser("seller-deleted", 1);
        Long buyerActive = insertUser("buyer-active", 0);

        Long buyerDeletedOrder = insertOrder(buyerDeleted, sellerActive, "buyer-deleted", "paid");
        Long sellerDeletedOrder = insertOrder(buyerActive, sellerDeleted, "seller-deleted", "shipped");

        jdbcTemplate.update("UPDATE users SET is_deleted = 1 WHERE id IN (?, ?)", buyerDeleted, sellerDeleted);

        AdminOrderQueryDTO query = new AdminOrderQueryDTO();
        query.setPage(1);
        query.setPageSize(20);
        query.setSortField("createTime");
        query.setSortOrder("asc");
        query.setKeyword(PREFIX);

        List<AdminOrderDTO> orders = orderMapper.listAdminOrders(query);

        assertEquals(2, orders.size());
        assertFalse(orders.stream().anyMatch(order -> buyerDeletedOrder.equals(order.getOrderId()) && !Boolean.TRUE.equals(order.getBuyerDeleted())));
        assertFalse(orders.stream().anyMatch(order -> sellerDeletedOrder.equals(order.getOrderId()) && !Boolean.TRUE.equals(order.getSellerDeleted())));

        AdminOrderDetailDTO buyerDeletedDetail = orderMapper.getAdminOrderDetail(buyerDeletedOrder);
        AdminOrderDetailDTO sellerDeletedDetail = orderMapper.getAdminOrderDetail(sellerDeletedOrder);

        assertNotNull(buyerDeletedDetail);
        assertNotNull(sellerDeletedDetail);
        assertEquals(Boolean.TRUE, buyerDeletedDetail.getBuyerDeleted());
        assertEquals(Boolean.TRUE, sellerDeletedDetail.getSellerDeleted());
        assertEquals(buyerDeleted, buyerDeletedDetail.getBuyerId());
        assertEquals(sellerDeleted, sellerDeletedDetail.getSellerId());

        verifiesControllerFiltersDetailsAndFlags(buyerDeletedOrder, sellerDeletedOrder);
    }

    private void verifiesControllerFiltersDetailsAndFlags(Long buyerDeletedOrder, Long sellerDeletedOrder) {
        Result<PageResult<AdminOrderDTO>> firstPage = adminOrderController.listOrders(
                1, 1, null, PREFIX, null, null, "createTime", "asc");
        assertEquals(2L, firstPage.getData().getTotal());
        assertEquals(1, firstPage.getData().getRecords().size());

        Result<PageResult<AdminOrderDTO>> keywordAndStatus = adminOrderController.listOrders(
                1, 20, "shipped", "seller-deleted", null, null, "createTime", "desc");
        assertEquals(1L, keywordAndStatus.getData().getTotal());
        assertEquals(sellerDeletedOrder, keywordAndStatus.getData().getRecords().get(0).getOrderId());

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Result<PageResult<AdminOrderDTO>> timeFiltered = adminOrderController.listOrders(
                1, 20, null, PREFIX, "2000-01-01 00:00:00", now, "createTime", "desc");
        assertEquals(2L, timeFiltered.getData().getTotal());

        assertThrows(BusinessException.class, () -> adminOrderController.listOrders(
                1, 20, null, PREFIX, null, null, "orderNo desc; drop table orders", "sideways"));

        assertThrows(BusinessException.class, () -> adminOrderController.getOrderDetail(Long.MAX_VALUE));

        OrderFlagRequest validFlag = new OrderFlagRequest();
        validFlag.setType("PAYMENT_RISK");
        validFlag.setRemark("P4ADMINB integration flag");
        BaseContext.setCurrentId(1L);
        try {
            assertEquals("标记成功", adminOrderController.flagOrder(buyerDeletedOrder, validFlag).getData());
            assertEquals("订单已存在该类型标记", adminOrderController.flagOrder(buyerDeletedOrder, validFlag).getData());
        } finally {
            BaseContext.removeCurrentId();
        }
        assertEquals(1, adminOrderController.listOrderFlags(buyerDeletedOrder).getData().size());

        OrderFlagRequest invalidFlag = new OrderFlagRequest();
        invalidFlag.setType("NOT_A_REAL_FLAG");
        assertThrows(BusinessException.class, () -> adminOrderController.flagOrder(sellerDeletedOrder, invalidFlag));
    }

    private Long insertUser(String suffix, int isSeller) {
        String username = PREFIX + "_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO users (username, password, mobile, nickname, status, is_seller, is_deleted) VALUES (?, ?, ?, ?, 'active', ?, 0)",
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
                "INSERT INTO orders (order_no, buyer_id, seller_id, total_amount, status, shipping_address) VALUES (?, ?, ?, ?, ?, ?)",
                orderNo, buyerId, sellerId, new BigDecimal("88.00"), status, "P4ADMINB integration address"
        );
        return jdbcTemplate.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
    }
}
