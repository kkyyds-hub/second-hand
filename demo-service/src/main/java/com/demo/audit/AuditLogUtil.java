package com.demo.audit;

import org.slf4j.Logger;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Day18 P4-S1 审计日志统一工具。
 *
 * 统一字段口径：
 * 1) auditId：单次动作唯一标识，便于跨日志检索；
 * 2) action：动作名（login/pay/cancel/ban...）；
 * 3) actorType/actorId：操作者类型与标识；
 * 4) targetType/targetId：操作对象类型与标识；
 * 5) result：SUCCESS/FAILED/IDEMPOTENT；
 * 6) ip：请求来源地址；
 * 7) detail/error：补充上下文。
 */
public final class AuditLogUtil {

    private AuditLogUtil() {
    }

    public static String newAuditId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void success(Logger log,
                               String auditId,
                               String action,
                               String actorType,
                               String actorId,
                               String targetType,
                               String targetId,
                               String result,
                               String detail) {
        log.info(
                "AUDIT auditId={}, action={}, actorType={}, actorId={}, targetType={}, targetId={}, result={}, ip={}, detail={}",
                safe(auditId), safe(action), safe(actorType), safe(actorId),
                safe(targetType), safe(targetId), safe(result), safe(resolveClientIp()), safe(detail)
        );
    }

    public static void failed(Logger log,
                              String auditId,
                              String action,
                              String actorType,
                              String actorId,
                              String targetType,
                              String targetId,
                              String error,
                              String detail) {
        log.warn(
                "AUDIT auditId={}, action={}, actorType={}, actorId={}, targetType={}, targetId={}, result=FAILED, ip={}, error={}, detail={}",
                safe(auditId), safe(action), safe(actorType), safe(actorId),
                safe(targetType), safe(targetId), safe(resolveClientIp()), safe(error), safe(detail)
        );
    }

    private static String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return "N/A";
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        if (request == null) {
            return "N/A";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }
}
