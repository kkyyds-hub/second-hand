package com.demo.admin;

import com.demo.constant.JwtClaimsConstant;
import com.demo.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理端权限隔离测试。
 *
 * 验证所有 /admin/** 接口（除 /admin/employee/login）的权限控制：
 * - 无 Token → 401
 * - 普通用户 Token → 401
 * - 过期管理员 Token → 401
 * - 合法管理员 Token → 允许访问
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminPermissionIsolationTest {

    private static final String ADMIN_SECRET = "dev-admin-secret-change-me";
    private static final String USER_SECRET = "dev-user-secret-change-me";
    private static final long TWO_HOURS_MS = 7_200_000L;

    @Autowired
    private TestRestTemplate restTemplate;

    /** 需要验证的管理端接口路径 */
    private static final String[] ADMIN_ENDPOINTS = {
            "/admin/dashboard/overview",
            "/admin/statistics/dau?date=2026-01-01",
            "/admin/users?page=1&pageSize=1",
            "/admin/products/pending-approval?page=1&pageSize=1",
            "/admin/orders?page=1&pageSize=1",
            "/admin/audit/overview",
            "/admin/ops/outbox/metrics",
            "/admin/credit?userId=1",
    };

    @Test
    void adminEndpointsRejectNoToken() {
        for (String endpoint : ADMIN_ENDPOINTS) {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, entity, String.class);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Endpoint " + endpoint + " should return 401 without token");
        }
    }

    @Test
    void adminEndpointsRejectUserToken() {
        String userToken = JwtUtil.createJWT(USER_SECRET, TWO_HOURS_MS, buildUserClaims(999L));

        for (String endpoint : ADMIN_ENDPOINTS) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", userToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, entity, String.class);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Endpoint " + endpoint + " should return 401 with user token");
        }
    }

    @Test
    void adminEndpointsRejectExpiredAdminToken() {
        // Create a token that expired 1 hour ago
        String expiredToken = JwtUtil.createJWT(ADMIN_SECRET, -3_600_000L, buildAdminClaims(1L));

        for (String endpoint : ADMIN_ENDPOINTS) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", expiredToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, entity, String.class);

            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Endpoint " + endpoint + " should return 401 with expired admin token");
        }
    }

    @Test
    void adminEndpointsAcceptValidAdminToken() {
        String adminToken = JwtUtil.createJWT(ADMIN_SECRET, TWO_HOURS_MS, buildAdminClaims(1L));

        for (String endpoint : ADMIN_ENDPOINTS) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", adminToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, entity, String.class);

            // Should NOT be 401 (may be 200, 400, or 500 depending on data, but not auth failure)
            assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    "Endpoint " + endpoint + " should NOT return 401 with valid admin token");
        }
    }

    @Test
    void loginEndpointIsPublic() {
        // /admin/employee/login should not require token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"loginId\":\"test\",\"password\":\"test\"}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/employee/login", HttpMethod.POST, entity, String.class);

        // Should not be 401 - may be 400 or other business error, but not auth rejection
        assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Login endpoint should not require token");
    }

    @Test
    void adminEndpointsRejectMalformedToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "this-is-not-a-valid-jwt-token");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/dashboard/overview", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "Should return 401 with malformed token");
    }

    @Test
    void errorResponsesDoNotLeakSensitiveInfo() {
        String adminToken = JwtUtil.createJWT(ADMIN_SECRET, TWO_HOURS_MS, buildAdminClaims(1L));
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Request a non-existent resource
        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/orders/999999999", HttpMethod.GET, entity, String.class);

        String body = response.getBody() != null ? response.getBody() : "";
        // Error responses should not leak SQL, stack traces, or table names
        assertFalse(body.toLowerCase().contains("sql"), "Response should not leak SQL");
        assertFalse(body.toLowerCase().contains("stacktrace"), "Response should not leak stack traces");
        assertFalse(body.toLowerCase().contains("table"), "Response should not leak table names");
    }

    // --- Helper methods ---

    private Map<String, Object> buildAdminClaims(Long empId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, empId);
        return claims;
    }

    private Map<String, Object> buildUserClaims(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, userId);
        return claims;
    }
}
