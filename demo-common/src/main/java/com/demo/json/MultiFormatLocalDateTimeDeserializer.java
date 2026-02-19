package com.demo.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * LocalDateTime 多格式反序列化器。
 *
 * 背景：
 * - 历史数据/不同链路可能出现不同时间格式（例如：
 *   1) 2026-02-08 17:27
 *   2) 2026-02-08 17:27:58
 *   3) 2026-02-08T17:27:58.8573385）
 * - MQ 消费时若只支持单一格式，会在监听器阶段直接转换失败，消息进入 fatal 分支。
 *
 * 目标：
 * - 保持现有项目时间口径不变的前提下，提高反序列化兼容性，避免“历史消息/异构格式”导致消费中断。
 */
public class MultiFormatLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    /**
     * 按优先级尝试解析的格式列表：
     * 1) ISO_LOCAL_DATE_TIME（支持 T + 可变小数秒）
     * 2) yyyy-MM-dd HH:mm:ss
     * 3) yyyy-MM-dd HH:mm
     */
    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    /**
     * 实现接口定义的方法。
     */
    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        // 1) 优先按常见格式依次尝试
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignore) {
                // 继续尝试下一种格式
            }
        }

        // 2) 兼容带时区偏移格式：2026-02-08T17:27:58+08:00
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (DateTimeParseException ignore) {
            // ignore
        }

        // 3) 兼容 UTC instant：2026-02-08T09:27:58Z
        try {
            return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignore) {
            // ignore
        }

        // 4) 所有格式都不匹配时，抛出明确异常
        throw ctxt.weirdStringException(
                text,
                LocalDateTime.class,
                "Unsupported datetime format. Supported: ISO-8601, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd HH:mm"
        );
    }
}

