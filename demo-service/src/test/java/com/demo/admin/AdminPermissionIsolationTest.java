package com.demo.admin;

import com.demo.constant.JwtClaimsConstant;
import com.demo.utils.JwtUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * - 合法管理员 Token → 允许访问（读接口 200 + code=1，写接口非 401/403/500）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminPermissionIsolationTest {

    private static final long TWO_HOURS_MS = 7_200_000L;

    @Value("${demo.jwt.admin-secret-key}")
    private String adminSecret;

    @Value("${demo.jwt.user-secret-key}")
    private String userSecret;

    @Autowired
    private TestRestTemplate restTemplate;

    /** 读接口（验证 200 + code=1 或非 401/403/500）。 */
    private static final String[] READ_ENDPOINTS = {
            "/admin/dashboard/overview",
            "/admin/statistics/dau?date=2026-01-01",
            "/admin/users?page=1&pageSize=1",
            "/admin/products/pending-approval?page=1&pageSize=1",
            "/admin/orders?page=1&pageSize=1",
            "/admin/audit/overview",
            "/admin/ops/outbox/metrics",
            "/admin/credit?userId=1",
    };

    /** 写接口（验证非 401/403/500，且返回统一 Result 结构）。 */
    private static final String[] WRITE_ENDPOINTS = {
            "/admin/after-sales/999999/arbitrate",
            "/admin/orders/999999/flags",
            "/admin/products/999999/force-off-shelf",
            "/admin/ops/outbox/publish-once?limit=1",
            "/admin/ops/tasks/ship-timeout/run-once?limit=1",
            "/admin/ops/tasks/refund/run-once?limit=1",
            "/admin/ops/tasks/ship-reminder/run-once?limit=1",
    };

    /** 所有端点（读 + 写）。 */
    private static final String[] ALL_ENDPOINTS = {
            "/admin/dashboard/overview",
            "/admin/statistics/dau?date=2026-01-01",
            "/admin/users?page=1&pageSize=1",
            "/admin/products/pending-approval?page=1&pageSize=1",
            "/admin/orders?page=1&pageSize=1",
            "/admin/audit/overview",
            "/admin/ops/outbox/metrics",
            "/admin/credit?userId=1",
            "/admin/after-sales/999999/arbitrate",
            "/admin/orders/999999/flags",
            "/admin/products/999999/force-off-shelf",
            "/admin/ops/outbox/publish-once?limit=1",
            "/admin/ops/tasks/ship-timeout/run-once?limit=1",
            "/admin/ops/tasks/refund/run-once?limit=1",
            "/admin/ops/tasks/ship-reminder/run-once?limit=1",
    };

    // ──────────────────────────────────────────────
    // No token tests
    // ──────────────────────────────────────────────

    @Nested
    class NoToken {

        @Test
        void readEndpointsRejectNoToken() {
            for (String endpoint : READ_ENDPOINTS) {
                HttpMethod method = resolveHttpMethod(endpoint);
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint, method, new HttpEntity<>(new HttpHeaders()), String.class);
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                        endpoint + " should return 401 without token");
            }
        }

        @Test
        void writeEndpointsRejectNoToken() {
            for (String endpoint : WRITE_ENDPOINTS) {
                HttpMethod method = resolveHttpMethod(endpoint);
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint, method, new HttpEntity<>(new HttpHeaders()), String.class);
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                        endpoint + " should return 401 without token");
            }
        }
    }

    // ──────────────────────────────────────────────
    // User token tests (not admin)
    // ──────────────────────────────────────────────

    @Nested
    class UserToken {

        @Test
        void readEndpointsRejectUserToken() {
            String userToken = JwtUtil.createJWT(userSecret, TWO_HOURS_MS, buildUserClaims(999L));
            for (String endpoint : READ_ENDPOINTS) {
                HttpMethod method = resolveHttpMethod(endpoint);
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", userToken);
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint, method, new HttpEntity<>(headers), String.class);
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                        endpoint + " should return 401 with user token");
            }
        }

        @Test
        void writeEndpointsRejectUserToken() {
            String userToken = JwtUtil.createJWT(userSecret, TWO_HOURS_MS, buildUserClaims(999L));
            for (String endpoint : WRITE_ENDPOINTS) {
                HttpMethod method = resolveHttpMethod(endpoint);
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", userToken);
                ResponseEntity<String> response = restTemplate.exchange(
                        endpoint, method, new HttpEntity<>(headers), String.class);
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                        endpoint + " should return 401 with user token");
            }
        }
    }

    // ──────────────────────────────────────────────
    // Expired admin token
    // ──────────────────────────────────────────────

    @Test
    void allEndpointsRejectExpiredAdminToken() {
        String expiredToken = JwtUtil.createJWT(adminSecret, -3_600_000L, buildAdminClaims(1L));
        for (String endpoint : ALL_ENDPOINTS) {
            HttpMethod method = resolveHttpMethod(endpoint);
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", expiredToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, method, new HttpEntity<>(headers), String.class);
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    endpoint + " should return 401 with expired admin token");
        }
    }

    // ──────────────────────────────────────────────
    // Valid admin token
    // ──────────────────────────────────────────────

    @Test
    void readEndpointsAcceptValidAdminToken() {
        String adminToken = JwtUtil.createJWT(adminSecret, TWO_HOURS_MS, buildAdminClaims(1L));
        for (String endpoint : READ_ENDPOINTS) {
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", adminToken);
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    endpoint + " must not return 401 with valid admin token");
            assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    endpoint + " must not return 403 with valid admin token");

            HttpStatus code = response.getStatusCode();
            if (code == HttpStatus.OK) {
                String body = response.getBody();
                assertNotNull(body, endpoint + " body must not be null");
                // Should be a unified Result structure
                assertTrue(body.contains("\"code\""),
                        endpoint + " response should contain unified Result.code field");
            }
            // Non-200 may happen for data-dependent endpoints, but must not be 401/403/500
            assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                    endpoint + " must not return 500 with valid admin token");
        }
    }

    @Test
    void writeEndpointsWithValidAdminTokenDoNotReturnAuthError() {
        String adminToken = JwtUtil.createJWT(adminSecret, TWO_HOURS_MS, buildAdminClaims(1L));
        for (String endpoint : WRITE_ENDPOINTS) {
            HttpMethod method = resolveHttpMethod(endpoint);
            HttpHeaders headers = new HttpHeaders();
            headers.set("token", adminToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // For PUT/POST with body, send minimal JSON body
            HttpEntity<?> requestEntity;
            if (endpoint.contains("/arbitrate")) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                requestEntity = new HttpEntity<>("{\"approved\":true,\"remark\":\"test\"}", headers);
            } else if (endpoint.contains("/flags")) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                requestEntity = new HttpEntity<>("{\"type\":\"PAYMENT_RISK\",\"remark\":\"test\"}", headers);
            } else if (endpoint.contains("/force-off-shelf")) {
                headers.setContentType(MediaType.APPLICATION_JSON);
                requestEntity = new HttpEntity<>("{\"reason\":\"test reason\"}", headers);
            } else {
                requestEntity = entity;
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, method, requestEntity, String.class);

            assertNotEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                    endpoint + " must not return 401 with valid admin token");
            assertNotEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                    endpoint + " must not return 403 with valid admin token");
            assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode(),
                    endpoint + " must not return 500 with valid admin token");

            String body = response.getBody();
            assertNotNull(body, endpoint + " body must not be null");
            // Should be a unified Result structure
            assertTrue(body.contains("\"code\""),
                    endpoint + " response should contain unified Result.code field");
        }
    }

    // ──────────────────────────────────────────────
    // Other tests (malformed token, login public, error safety)
    // ──────────────────────────────────────────────

    @Test
    void loginEndpointIsPublic() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"loginId\":\"test\",\"password\":\"test\"}", headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/employee/login", HttpMethod.POST, entity, String.class);
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
        String adminToken = JwtUtil.createJWT(adminSecret, TWO_HOURS_MS, buildAdminClaims(1L));
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/orders/999999999", HttpMethod.GET, entity, String.class);
        String body = response.getBody() != null ? response.getBody() : "";
        assertFalse(body.toLowerCase().contains("sql"), "Response should not leak SQL");
        assertFalse(body.toLowerCase().contains("stacktrace"), "Response should not leak stack traces");
        assertFalse(body.toLowerCase().contains("table"), "Response should not leak table names");
    }

    // ──────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────

    /**
     * Resolve HTTP method from endpoint path:
     * - Read endpoints are GET
     * - Write endpoints use PUT or POST per their controller mapping
     */
    private HttpMethod resolveHttpMethod(String endpoint) {
        if (endpoint.contains("/arbitrate") || endpoint.contains("/force-off-shelf")) {
            return HttpMethod.PUT;
        }
        if (endpoint.contains("/flags") || endpoint.contains("/run-once") || endpoint.contains("/publish-once")) {
            return HttpMethod.POST;
        }
        return HttpMethod.GET;
    }

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
