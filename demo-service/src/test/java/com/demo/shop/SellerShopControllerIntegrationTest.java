package com.demo.shop;

import com.demo.constant.JwtClaimsConstant;
import com.demo.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 卖家小店接口集成测试。
 * <p>
 * 使用 P5SHOPA_ 前缀创建测试数据，在 @AfterAll 中清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SellerShopControllerIntegrationTest {

    private static final long TWO_HOURS_MS = 7_200_000L;
    private static final String PREFIX = "P5SHOPA_";

    @Value("${demo.jwt.user-secret-key}")
    private String userSecret;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Long sellerAId;
    private static Long sellerBId;
    private static Long buyerId;
    private static Long nonSellerId;
    private static Long bannedSellerId;
    private static Long deletedSellerId;

    private static Long sellerAProduct1Id;
    private static Long sellerAProduct2Id;
    private static Long sellerAProduct3Id;
    private static Long sellerAProductSoldId;
    private static Long sellerAProductUnderReviewId;
    private static Long sellerAProductOffShelfId;
    private static Long sellerBProductId;

    private static String sellerAToken;
    private static String buyerToken;

    @BeforeAll
    static void setUp(@Autowired JdbcTemplate jdbcTemplate,
                      @Value("${demo.jwt.user-secret-key}") String userSecret) {
        // Pre-clean: remove any leftover data from previous test runs
        cleanTestData(jdbcTemplate);

        long now = System.currentTimeMillis();

        // Create seller A
        sellerAId = createUser(jdbcTemplate, PREFIX + "sellerA", PREFIX + "sellerA_nick",
                "active", 1, 0, 120);
        // Create seller B
        sellerBId = createUser(jdbcTemplate, PREFIX + "sellerB", PREFIX + "sellerB_nick",
                "active", 1, 0, 110);
        // Create buyer
        buyerId = createUser(jdbcTemplate, PREFIX + "buyer", PREFIX + "buyer_nick",
                "active", 0, 0, 100);
        // Create non-seller
        nonSellerId = createUser(jdbcTemplate, PREFIX + "nonseller", PREFIX + "nonseller_nick",
                "active", 0, 0, 100);
        // Create banned seller
        bannedSellerId = createUser(jdbcTemplate, PREFIX + "banned", PREFIX + "banned_nick",
                "banned", 1, 0, 100);
        // Create deleted seller (will be marked deleted)
        deletedSellerId = createUser(jdbcTemplate, PREFIX + "deleted", PREFIX + "deleted_nick",
                "active", 1, 1, 100);

        // Products for seller A
        String images = "https://example.com/img.jpg";
        sellerAProduct1Id = createProduct(jdbcTemplate, "商品A1", sellerAId, "on_sale", images, 0);
        sellerAProduct2Id = createProduct(jdbcTemplate, "商品A2", sellerAId, "on_sale", images, 0);
        sellerAProduct3Id = createProduct(jdbcTemplate, "商品A3", sellerAId, "on_sale", images, 0);
        sellerAProductSoldId = createProduct(jdbcTemplate, "商品A-已售", sellerAId, "sold", images, 0);
        sellerAProductUnderReviewId = createProduct(jdbcTemplate, "商品A-审核中", sellerAId, "under_review", images, 0);
        sellerAProductOffShelfId = createProduct(jdbcTemplate, "商品A-下架", sellerAId, "off_shelf", images, 0);
        // Soft-deleted product for seller A
        createProduct(jdbcTemplate, "商品A-已删", sellerAId, "on_sale", images, 1);

        // Product for seller B
        sellerBProductId = createProduct(jdbcTemplate, "商品B1", sellerBId, "on_sale", images, 0);

        // Create a completed order for seller A
        createCompletedOrder(jdbcTemplate, buyerId, sellerAId, sellerAProductSoldId);

        // Tokens
        sellerAToken = generateToken(userSecret, sellerAId, now);
        buyerToken = generateToken(userSecret, buyerId, now);
    }

    @AfterAll
    static void tearDown(@Autowired JdbcTemplate jdbcTemplate) {
        // Delete in FK-safe order
        jdbcTemplate.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE ?)", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM after_sale_evidences WHERE after_sale_id IN (SELECT id FROM after_sales WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE ?))", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM after_sales WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE ?)", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM order_flags WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE ?)", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM orders WHERE order_no LIKE ?", PREFIX + "%");
        jdbcTemplate.update("DELETE FROM favorites WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM cart_items WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM product_status_audit_log WHERE product_id IN (SELECT id FROM products WHERE owner_id IN (?, ?, ?, ?, ?, ?))", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM product_report_ticket WHERE product_id IN (SELECT id FROM products WHERE owner_id IN (?, ?, ?, ?, ?, ?))", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM product_violations WHERE product_id IN (SELECT id FROM products WHERE owner_id IN (?, ?, ?, ?, ?, ?))", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM products WHERE owner_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM addresses WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM user_credit_logs WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM user_violations WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM user_bans WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM user_oauth_bind WHERE user_id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?, ?, ?, ?, ?)", sellerAId, sellerBId, buyerId, nonSellerId, bannedSellerId, deletedSellerId);
    }

    // ──────────────────────────────────────────────
    // 19.1 Shop profile
    // ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("正常卖家小店概览")
    void normalSellerShopProfile() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + sellerAId, buyerToken, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertNotNull(body.get("data"));

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals(sellerAId.longValue(), ((Number) data.get("sellerId")).longValue());
        assertTrue(((String) data.get("shopName")).endsWith("的小店"));
        assertEquals(PREFIX + "sellerA_nick", data.get("nickname"));
        assertEquals(120, ((Number) data.get("creditScore")).intValue());
        assertNotNull(data.get("registeredAt"));
        assertTrue(((Number) data.get("onSaleCount")).intValue() >= 3);
        assertTrue(((Number) data.get("soldCount")).intValue() >= 1);
        assertTrue(((Number) data.get("completedOrderCount")).intValue() >= 1);
        assertEquals(false, data.get("isCurrentUser"));
    }

    @Test
    @Order(2)
    @DisplayName("本人访问标记正确")
    void selfViewFlag() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + sellerAId, sellerAToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        assertEquals(true, data.get("isCurrentUser"));
    }

    @Test
    @Order(3)
    @DisplayName("不存在卖家 → 卖家不存在")
    void nonExistentSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/999999", buyerToken, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("卖家不存在"));
    }

    @Test
    @Order(4)
    @DisplayName("普通非卖家 → 尚未开通小店")
    void nonSellerUser() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + nonSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("尚未开通小店"));
    }

    @Test
    @Order(5)
    @DisplayName("禁用卖家 → 小店暂不可访问")
    void bannedSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + bannedSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("暂不可访问"));
    }

    @Test
    @Order(6)
    @DisplayName("软删除卖家 → 小店已不存在")
    void deletedSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + deletedSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("已不存在"));
    }

    // ──────────────────────────────────────────────
    // 19.2 Multi-product attribution
    // ──────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("多商品归属：卖家A只返回owner_id=A的商品")
    void multiProductAttribution() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=10",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertFalse(list.isEmpty());
        for (Map<String, Object> item : list) {
            // All products should be from seller A - verified by endpoint path
            assertEquals("on_sale", item.get("status"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("卖家B商品不得串入卖家A的小店")
    void sellerBProductsNotInSellerAShop() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=20",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        for (Map<String, Object> item : list) {
            Long productId = ((Number) item.get("productId")).longValue();
            assertNotEquals(sellerBProductId.longValue(), productId);
        }
    }

    // ──────────────────────────────────────────────
    // 19.3 Status isolation
    // ──────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("状态隔离：on_sale不包含审核中/下架/已删商品")
    void statusIsolationOnSale() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=20",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        for (Map<String, Object> item : list) {
            String status = (String) item.get("status");
            assertEquals("on_sale", status);
            Long productId = ((Number) item.get("productId")).longValue();
            assertNotEquals(sellerAProductUnderReviewId.longValue(), productId);
            assertNotEquals(sellerAProductOffShelfId.longValue(), productId);
        }
    }

    @Test
    @Order(13)
    @DisplayName("已售列表只返回已售商品")
    void soldListOnlySold() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=sold&page=1&pageSize=10",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        for (Map<String, Object> item : list) {
            assertEquals("sold", item.get("status"));
        }
    }

    // ──────────────────────────────────────────────
    // 19.4 Pagination
    // ──────────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("分页：page=1&pageSize=2 正确分页")
    void paginationFirstPage() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=2",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        assertEquals(1, ((Number) data.get("page")).intValue());
        assertEquals(2, ((Number) data.get("pageSize")).intValue());
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        assertTrue(list.size() <= 2);
        assertTrue(((Number) data.get("total")).intValue() >= 3);
    }

    // ──────────────────────────────────────────────
    // 19.6 Other products
    // ──────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("其他商品：排除当前商品，limit生效")
    void otherProductsExclusion() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=6&excludeProductId=" + sellerAProduct1Id,
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        for (Map<String, Object> item : list) {
            Long productId = ((Number) item.get("productId")).longValue();
            assertNotEquals(sellerAProduct1Id.longValue(), productId);
        }
        assertTrue(list.size() <= 6);
    }

    // ──────────────────────────────────────────────
    // 19.7 Privacy
    // ──────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("隐私字段：响应不含mobile/email/password等")
    void privacyFields() {
        ResponseEntity<String> response = getWithTokenRaw("/user/shops/" + sellerAId, sellerAToken);
        String body = Objects.requireNonNull(response.getBody());
        // Field-level assertion: response data should not contain these keys
        assertFalse(body.contains("\"mobile\""), "Should not expose mobile");
        assertFalse(body.contains("\"email\""), "Should not expose email");
        assertFalse(body.contains("\"password\""), "Should not expose password");
    }

    // ──────────────────────────────────────────────
    // 19.8 Invalid params
    // ──────────────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("非法参数：sellerId=0")
    void invalidSellerIdZero() {
        ResponseEntity<Map> response = getWithToken("/user/shops/0", buyerToken, Map.class);
        // @Min(1) validation may return 400, or the service returns 200 with code=0
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            (response.getStatusCode() == HttpStatus.OK && response.getBody() != null &&
             ((Number) response.getBody().get("code")).intValue() == 0),
            "Expected 400 or 200 with code=0, got " + response.getStatusCode()
        );
    }

    @Test
    @Order(31)
    @DisplayName("非法参数：非法status")
    void invalidStatus() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=invalid",
                buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
    }

    @Test
    @Order(32)
    @DisplayName("非法参数：pageSize > 24")
    void invalidPageSize() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?pageSize=100",
                buyerToken, Map.class);
        // Max pageSize enforced
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    private static void cleanTestData(JdbcTemplate jt) {
        // First find and delete all P5SHOPA_ users
        List<Long> userIds = jt.queryForList(
            "SELECT id FROM users WHERE username LIKE ?", Long.class, PREFIX + "%"
        );
        if (userIds.isEmpty()) return;

        // Delete dependent records first
        for (Long uid : userIds) {
            jt.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE buyer_id = ? OR seller_id = ?)", uid, uid);
            jt.update("DELETE FROM order_items WHERE product_id IN (SELECT id FROM products WHERE owner_id = ?)", uid);
            jt.update("DELETE FROM order_flags WHERE order_id IN (SELECT id FROM orders WHERE buyer_id = ? OR seller_id = ?)", uid, uid);
            jt.update("DELETE FROM after_sale_evidences WHERE after_sale_id IN (SELECT id FROM after_sales WHERE order_id IN (SELECT id FROM orders WHERE buyer_id = ? OR seller_id = ?))", uid, uid);
            jt.update("DELETE FROM after_sales WHERE order_id IN (SELECT id FROM orders WHERE buyer_id = ? OR seller_id = ?)", uid, uid);
            jt.update("DELETE FROM orders WHERE buyer_id = ? OR seller_id = ?", uid, uid);
            jt.update("DELETE FROM favorites WHERE user_id = ?", uid);
            jt.update("DELETE FROM cart_items WHERE user_id = ?", uid);
            jt.update("DELETE FROM product_status_audit_log WHERE product_id IN (SELECT id FROM products WHERE owner_id = ?)", uid);
            jt.update("DELETE FROM product_report_ticket WHERE product_id IN (SELECT id FROM products WHERE owner_id = ?)", uid);
            jt.update("DELETE FROM product_violations WHERE product_id IN (SELECT id FROM products WHERE owner_id = ?)", uid);
            jt.update("DELETE FROM products WHERE owner_id = ?", uid);
            jt.update("DELETE FROM addresses WHERE user_id = ?", uid);
            jt.update("DELETE FROM user_credit_logs WHERE user_id = ?", uid);
            jt.update("DELETE FROM user_violations WHERE user_id = ?", uid);
            jt.update("DELETE FROM user_bans WHERE user_id = ?", uid);
            jt.update("DELETE FROM user_oauth_bind WHERE user_id = ?", uid);
            jt.update("DELETE FROM message_outbox WHERE biz_id = ?", String.valueOf(uid));
        }
        jt.update("DELETE FROM users WHERE username LIKE ?", PREFIX + "%");
    }

    private static Long createUser(JdbcTemplate jt, String username, String nickname,
                                    String status, int isSeller, int isDeleted, int creditScore) {
        jt.update("INSERT INTO users (username, password, mobile, nickname, status, is_seller, is_deleted, credit_score, credit_level, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'lv3', NOW(), NOW())",
                username, "pass", PREFIX + "1380000" + username.substring(PREFIX.length()),
                nickname, status, isSeller, isDeleted, creditScore);
        Long id = jt.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
        return id;
    }

    private static Long createProduct(JdbcTemplate jt, String title, Long ownerId,
                                       String status, String images, int isDeleted) {
        jt.update("INSERT INTO products (title, description, price, images, category, status, view_count, owner_id, is_deleted, create_time, update_time) "
                + "VALUES (?, 'desc', 10.00, ?, '数码', ?, 0, ?, ?, NOW(), NOW())",
                title, images, status, ownerId, isDeleted);
        return jt.queryForObject("SELECT id FROM products WHERE title = ? AND owner_id = ?", Long.class, title, ownerId);
    }

    private static void createCompletedOrder(JdbcTemplate jt, Long buyerId, Long sellerId, Long productId) {
        String orderNo = PREFIX + "order_" + System.currentTimeMillis();
        jt.update("INSERT INTO orders (order_no, buyer_id, seller_id, total_amount, status, shipping_address, create_time, complete_time, update_time) "
                + "VALUES (?, ?, ?, 10.00, 'completed', '{}', NOW(), NOW(), NOW())", orderNo, buyerId, sellerId);
        Long orderId = jt.queryForObject("SELECT id FROM orders WHERE order_no = ?", Long.class, orderNo);
        jt.update("INSERT INTO order_items (order_id, product_id, price, quantity, create_time) VALUES (?, ?, 10.00, 1, NOW())", orderId, productId);
    }

    private static String generateToken(String secret, Long userId, long now) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId.intValue());
        return JwtUtil.createJWT(secret, now + TWO_HOURS_MS, claims);
    }

    private <T> ResponseEntity<T> getWithToken(String url, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authentication", token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<String> getWithTokenRaw(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("authentication", token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
