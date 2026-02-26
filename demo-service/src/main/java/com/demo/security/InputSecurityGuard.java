package com.demo.security;

import com.demo.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 输入安全守卫：
 * 1) 文本字段统一做 trim/长度/危险片段校验；
 * 2) 动态排序字段统一走白名单；
 * 3) 保持“参数不合法即失败”，避免静默降级掩盖风险输入。
 *
 * 设计口径（Day18 P3-S2）：
 * 1) 本类只做“输入规范化 + 拒绝式校验”，不承担业务语义判断；
 * 2) 返回值均为可直接入库/参与查询构建的“安全值”；
 * 3) 校验失败统一抛 BusinessException，保证控制器层返回一致的业务失败语义。
 */
public final class InputSecurityGuard {

    /**
     * XSS 高风险片段最小拦截规则。
     *
     * 命中即拒绝，核心目的不是“清洗后继续放行”，而是“显式阻断风险输入”：
     * 1) < 或 >：阻断常见 HTML 标签注入；
     * 2) javascript:：阻断 URI scheme 载荷；
     * 3) onxxx=：阻断内联事件处理器。
     */
    private static final Pattern XSS_RISKY_PATTERN =
            Pattern.compile("(?i)(<|>|javascript:|on\\w+\\s*=)");

    private InputSecurityGuard() {
    }

    /**
     * 规范化纯文本输入（通用入口）。
     *
     * 固定执行顺序：
     * 1) trim：去除首尾空白，避免“仅空格”绕过；
     * 2) required 判定：必填字段为空则失败；
     * 3) 长度校验：阻断超长输入，避免异常膨胀；
     * 4) XSS 风险片段校验：命中即拒绝；
     * 5) 返回规范化后的安全值。
     *
     * @param value 原始输入
     * @param fieldName 字段名（用于错误提示）
     * @param maxLen 允许最大长度
     * @param required 是否必填
     * @return 校验通过后的规范化字符串；非必填且为空时返回 null
     */
    public static String normalizePlainText(String value, String fieldName, int maxLen, boolean required) {
        String trimmed = value == null ? null : value.trim();
        if (!StringUtils.hasText(trimmed)) {
            if (required) {
                throw new BusinessException(fieldName + "不能为空");
            }
            return null;
        }

        if (trimmed.length() > maxLen) {
            throw new BusinessException(fieldName + "长度不能超过" + maxLen);
        }
        if (XSS_RISKY_PATTERN.matcher(trimmed).find()) {
            throw new BusinessException(fieldName + "包含非法脚本或HTML片段");
        }
        return trimmed;
    }

    /**
     * 规范化排序字段。
     *
     * 口径：
     * 1) 未传值使用默认字段；
     * 2) 仅允许白名单字段参与 SQL 动态分支；
     * 3) 非白名单直接失败，不做“偷偷降级”。
     */
    public static String normalizeSortField(String raw, Set<String> whitelist, String defaultField) {
        if (!StringUtils.hasText(raw)) {
            return defaultField;
        }
        String field = raw.trim();
        if (!whitelist.contains(field)) {
            throw new BusinessException("sortField 仅支持: " + String.join("/", whitelist));
        }
        return field;
    }

    /**
     * 规范化排序方向。
     *
     * 口径：
     * 1) 未传值使用默认方向；
     * 2) 仅允许 asc / desc（大小写不敏感）；
     * 3) 非法值直接失败，避免拼接出不可预期 SQL 分支。
     */
    public static String normalizeSortOrder(String raw, String defaultOrder) {
        if (!StringUtils.hasText(raw)) {
            return defaultOrder;
        }
        String order = raw.trim().toLowerCase();
        if (!"asc".equals(order) && !"desc".equals(order)) {
            throw new BusinessException("sortOrder 仅支持 asc/desc");
        }
        return order;
    }
}
