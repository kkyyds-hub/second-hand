package com.demo.logistics;

import com.demo.config.LogisticsProperties;
import com.demo.dto.logistics.LogisticsTrackResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * delivery-tracker 第三方 provider（可选演示实现）。
 *
 * 当前阶段定位：
 * 1) 打通调用链（配置 -> HTTP 请求 -> 日志）
 * 2) 失败可降级，不影响订单主流程
 *
 * 说明：
 * - 当前版本先返回空轨迹（骨架实现）
 * - 后续可在 TODO 位置完成响应 JSON -> LogisticsTrackResult 的映射
 */
@Slf4j
@Component
public class DeliveryTrackerProvider implements LogisticsProvider {

    private final LogisticsProperties logisticsProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public DeliveryTrackerProvider(LogisticsProperties logisticsProperties) {
        this.logisticsProperties = logisticsProperties;
    }

    @Override
    public String getName() {
        return "delivery-tracker";
    }

    /**
     * 查询第三方轨迹。
     *
     * 降级策略：
     * - apiKey/baseUrl/trackingNo 为空：直接返回空轨迹
     * - HTTP 请求异常：记录 warn 日志并返回空轨迹
     */
    @Override
    public LogisticsTrackResult query(String shippingCompany, String trackingNo, LocalDateTime shipTime) {
        LogisticsTrackResult result = new LogisticsTrackResult();
        result.setProvider(getName());
        result.setLastSyncTime(LocalDateTime.now());
        result.setTrace(Collections.emptyList());

        String apiKey = logisticsProperties.getDeliveryTracker().getApiKey();
        String baseUrl = logisticsProperties.getDeliveryTracker().getBaseUrl();
        if (isBlank(apiKey) || isBlank(baseUrl) || isBlank(trackingNo)) {
            return result;
        }

        try {
            // URL 仅作演示占位，后续按 delivery-tracker 实际文档调整路径与参数
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/trackings/{trackingNo}")
                    .queryParam("courier", shippingCompany)
                    .buildAndExpand(trackingNo)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(apiKey);

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.debug("delivery-tracker query ok, trackingNo={}, status={}", trackingNo, response.getStatusCodeValue());

            // TODO: 解析 response.getBody()，映射为 LogisticsTrackResult.trace
            // 当前阶段先不解析，避免在 provider 骨架阶段引入过多耦合
        } catch (Exception ex) {
            log.warn("delivery-tracker query failed, trackingNo={}", trackingNo, ex);
        }
        return result;
    }

    /**
     * 字符串空值判断工具方法。
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
