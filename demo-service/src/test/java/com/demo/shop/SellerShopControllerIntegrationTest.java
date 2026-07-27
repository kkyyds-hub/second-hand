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

import java.util.*;

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
        cleanTestData(jdbcTemplate);

        long now = System.currentTimeMillis();

        sellerAId = createUser(jdbcTemplate, PREFIX + "sellerA", PREFIX + "sellerA_nick",
                "active", 1, 0, 120);
        sellerBId = createUser(jdbcTemplate, PREFIX + "sellerB", PREFIX + "sellerB_nick",
                "active", 1, 0, 110);
        buyerId = createUser(jdbcTemplate, PREFIX + "buyer", PREFIX + "buyer_nick",
                "active", 0, 0, 100);
        nonSellerId = createUser(jdbcTemplate, PREFIX + "nonseller", PREFIX + "nonseller_nick",
                "active", 0, 0, 100);
        bannedSellerId = createUser(jdbcTemplate, PREFIX + "banned", PREFIX + "banned_nick",
                "banned", 1, 0, 100);
        deletedSellerId = createUser(jdbcTemplate, PREFIX + "deleted", PREFIX + "deleted_nick",
                "active", 1, 1, 100);

        String images = "https://example.com/img.jpg";
        sellerAProduct1Id = createProduct(jdbcTemplate, "商品A1", sellerAId, "on_sale", images, 0);
        sellerAProduct2Id = createProduct(jdbcTemplate, "商品A2", sellerAId, "on_sale", images, 0);
        sellerAProduct3Id = createProduct(jdbcTemplate, "商品A3", sellerAId, "on_sale", images, 0);
        sellerAProductSoldId = createProduct(jdbcTemplate, "商品A-已售", sellerAId, "sold", images, 0);
        sellerAProductUnderReviewId = createProduct(jdbcTemplate, "商品A-审核中", sellerAId, "under_review", images, 0);
        sellerAProductOffShelfId = createProduct(jdbcTemplate, "商品A-下架", sellerAId, "off_shelf", images, 0);
        createProduct(jdbcTemplate, "商品A-已删", sellerAId, "on_sale", images, 1);

        sellerBProductId = createProduct(jdbcTemplate, "商品B1", sellerBId, "on_sale", images, 0);

        createCompletedOrder(jdbcTemplate, buyerId, sellerAId, sellerAProductSoldId);

        sellerAToken = generateToken(userSecret, sellerAId, now);
        buyerToken = generateToken(userSecret, buyerId, now);
    }

    @AfterAll
    static void tearDown(@Autowired JdbcTemplate jdbcTemplate) {
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

    // ========================================
    // Shop profile tests
    // ========================================

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
        // soldTime must never appear in response
        String raw = getWithTokenRaw("/user/shops/" + sellerAId + "/products?status=sold&page=1&pageSize=10", buyerToken).getBody();
        assertFalse(Objects.requireNonNull(raw).contains("\"soldTime\""), "soldTime must not appear in response");
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
    @DisplayName("不存在卖家 -> 卖家不存在")
    void nonExistentSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/999999", buyerToken, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("卖家不存在"));
    }

    @Test
    @Order(4)
    @DisplayName("普通非卖家 -> 尚未开通小店")
    void nonSellerUser() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + nonSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("尚未开通小店"));
    }

    @Test
    @Order(5)
    @DisplayName("禁用卖家 -> 小店暂不可访问")
    void bannedSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + bannedSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("暂不可访问"));
    }

    @Test
    @Order(6)
    @DisplayName("软删除卖家 -> 小店已不存在")
    void deletedSeller() {
        ResponseEntity<Map> response = getWithToken("/user/shops/" + deletedSellerId, buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
        assertTrue(((String) body.get("msg")).contains("已不存在"));
    }

    // ========================================
    // Multi-product attribution
    // ========================================

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

    // ========================================
    // Status isolation
    // ========================================

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

    // ========================================
    // Pagination
    // ========================================

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

    @Test
    @Order(15)
    @DisplayName("分页第二页无重复商品")
    void paginationSecondPageHasNoDuplicateProducts() {
        // page1
        ResponseEntity<Map> resp1 = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=1&pageSize=2",
                buyerToken, Map.class);
        Map<String, Object> data1 = (Map<String, Object>) Objects.requireNonNull(resp1.getBody()).get("data");
        List<Map<String, Object>> page1 = (List<Map<String, Object>>) data1.get("list");
        int total = ((Number) data1.get("total")).intValue();

        // page2
        ResponseEntity<Map> resp2 = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=on_sale&page=2&pageSize=2",
                buyerToken, Map.class);
        Map<String, Object> data2 = (Map<String, Object>) Objects.requireNonNull(resp2.getBody()).get("data");
        List<Map<String, Object>> page2 = (List<Map<String, Object>>) data2.get("list");

        // No overlap between page1 and page2
        Set<Long> page1Ids = new HashSet<>();
        for (Map<String, Object> item : page1) {
            page1Ids.add(((Number) item.get("productId")).longValue());
        }
        for (Map<String, Object> item : page2) {
            Long id = ((Number) item.get("productId")).longValue();
            assertFalse(page1Ids.contains(id), "Product " + id + " appears on both pages");
        }

        // Combined count matches total expectation
        int combinedSize = page1.size() + page2.size();
        assertTrue(combinedSize <= total, "Combined pages should not exceed total");
    }

    // ========================================
    // Other products exclusion
    // ========================================

    @Test
    @Order(16)
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

    // ========================================
    // soldProductResponseHasNoFakeSoldTime
    // ========================================

    @Test
    @Order(17)
    @DisplayName("已售商品响应不含soldTime字段")
    void soldProductResponseHasNoFakeSoldTime() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=sold&page=1&pageSize=10",
                buyerToken, Map.class);
        Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(response.getBody()).get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("list");
        for (Map<String, Object> item : list) {
            assertNull(item.get("soldTime"), "soldTime field must not be present: " + item);
        }
    }

    // ========================================
    // Privacy
    // ========================================

    @Test
    @Order(20)
    @DisplayName("隐私字段：响应不含mobile/email/password等")
    void privacyFields() {
        ResponseEntity<String> response = getWithTokenRaw("/user/shops/" + sellerAId, sellerAToken);
        String body = Objects.requireNonNull(response.getBody());
        assertFalse(body.contains("\"mobile\""), "Should not expose mobile");
        assertFalse(body.contains("\"email\""), "Should not expose email");
        assertFalse(body.contains("\"password\""), "Should not expose password");
    }

    // ========================================
    // Invalid params
    // ========================================

    @Test
    @Order(30)
    @DisplayName("非法参数：sellerId=0")
    void invalidSellerIdZero() {
        ResponseEntity<Map> response = getWithToken("/user/shops/0", buyerToken, Map.class);
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            (response.getStatusCode() == HttpStatus.OK && response.getBody() != null &&
             ((Number) response.getBody().get("code")).intValue() == 0),
            "Expected 400 or 200 with code=0, got " + response.getStatusCode()
        );
    }

    @Test
    @Order(31)
    @DisplayName("非法参数：negative sellerId")
    void negativeSellerIdIsRejected() {
        ResponseEntity<Map> response = getWithToken("/user/shops/-1", buyerToken, Map.class);
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            (response.getStatusCode() == HttpStatus.OK && response.getBody() != null &&
             ((Number) response.getBody().get("code")).intValue() == 0),
            "Expected 400 or 200 with code=0 for negative sellerId, got " + response.getStatusCode()
        );
    }

    @Test
    @Order(32)
    @DisplayName("非法参数：非法status")
    void invalidStatus() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?status=invalid",
                buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        assertEquals(0, ((Number) body.get("code")).intValue());
    }

    @Test
    @Order(33)
    @DisplayName("非法参数：pageSize超过最大值")
    void pageSizeAboveMaximumIsRejected() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?pageSize=100",
                buyerToken, Map.class);
        Map<String, Object> body = Objects.requireNonNull(response.getBody());
        // @Max(24) should reject
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            ((Number) body.get("code")).intValue() == 0,
            "Expected 400 or code=0 for pageSize > 24"
        );
    }

    @Test
    @Order(34)
    @DisplayName("非法参数：excludeProductId=0 被拒绝")
    void zeroExcludeProductIdIsRejected() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?excludeProductId=0",
                buyerToken, Map.class);
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            (response.getStatusCode() == HttpStatus.OK && response.getBody() != null &&
             ((Number) response.getBody().get("code")).intValue() == 0),
            "Expected 400 or code=0 for excludeProductId=0"
        );
    }

    @Test
    @Order(35)
    @DisplayName("非法参数：excludeProductId=-1 被拒绝")
    void negativeExcludeProductIdIsRejected() {
        ResponseEntity<Map> response = getWithToken(
                "/user/shops/" + sellerAId + "/products?excludeProductId=-1",
                buyerToken, Map.class);
        assertTrue(
            response.getStatusCode() == HttpStatus.BAD_REQUEST ||
            (response.getStatusCode() == HttpStatus.OK && response.getBody() != null &&
             ((Number) response.getBody().get("code")).intValue() == 0),
            "Expected 400 or code=0 for excludeProductId=-1"
        );
    }

    // ========================================
    // Helpers
    // ========================================

    private static void cleanTestData(JdbcTemplate jt) {
        List<Long> userIds = jt.queryForList(
            "SELECT id FROM users WHERE username LIKE ?", Long.class, PREFIX + "%"
        );
        if (userIds.isEmpty()) return;

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
        }
        jt.update("DELETE FROM users WHERE username LIKE ?", PREFIX + "%");
    }

    private static Long createUser(JdbcTemplate jt, String username, String nickname,
                                    String status, int isSeller, int isDeleted, int creditScore) {
        jt.update("INSERT INTO users (username, password, mobile, nickname, status, is_seller, is_deleted, credit_score, credit_level, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'lv3', NOW(), NOW())",
                username, "pass", PREFIX + "1380000" + username.substring(PREFIX.length()),
                nickname, status, isSeller, isDeleted, creditScore);
        return jt.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
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
