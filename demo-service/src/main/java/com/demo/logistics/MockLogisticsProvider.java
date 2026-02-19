package com.demo.logistics;

import com.demo.config.LogisticsProperties;
import com.demo.dto.logistics.LogisticsTrackNode;
import com.demo.dto.logistics.LogisticsTrackResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mock 物流 Provider（Step4 完整版）
 *
 * 设计目标：
 * 1) 零成本：不依赖任何外部 API。
 * 2) 稳定回归：同一 trackingNo 多次查询，轨迹时间和路线稳定可复现。
 * 3) 可演示：支持 fast-forward（快速推进节点，便于演示）。
 * 4) 真实感：仅返回“当前时间之前”的节点，模拟物流逐步推进。
 */
@Component
@Slf4j
public class MockLogisticsProvider implements LogisticsProvider {

    /**
     * 支持的固定时间格式（回归使用）。
     * - 2026-02-12 10:30:00
     * - 2026-02-12T10:30:00
     */
    private static final DateTimeFormatter FIXED_NOW_FMT_1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FIXED_NOW_FMT_2 = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 预设城市池。用于生成“看起来真实”的中转路径。
     */
    private static final String[] CITY_POOL = {
            "上海", "北京", "广州", "深圳", "杭州", "南京", "成都", "武汉"
    };

    private final LogisticsProperties logisticsProperties;

    /**
     * 构造函数，初始化当前组件依赖。
     */
    public MockLogisticsProvider(LogisticsProperties logisticsProperties) {
        this.logisticsProperties = logisticsProperties;
    }

    /**
     * 查询并返回相关结果。
     */
    @Override
    public String getName() {
        return "mock";
    }

    /**
     * 查询 mock 轨迹。
     *
     * 核心规则：
     * - baseTime：以 shipTime 为准；如果 shipTime 为空，默认回拨 3 小时作为起点。
     * - minutePlan：根据 fast-forward 选择阶段时间计划。
     * - jitter：基于 trackingNo 的稳定偏移，避免“每单轨迹完全一致”。
     * - 返回策略：只返回当前时间之前的节点，且至少保证返回 1 条。
     */
    @Override
    public LogisticsTrackResult query(String shippingCompany, String trackingNo, LocalDateTime shipTime) {
        LogisticsTrackResult result = new LogisticsTrackResult();
        result.setProvider(getName());
        LocalDateTime now = resolveNow();
        result.setLastSyncTime(now);

        // 如果 mock 被关闭，返回空轨迹（不抛异常，保持主流程可用）
        if (!logisticsProperties.getMock().isEnabled()) {
            result.setTrace(new ArrayList<>());
            return result;
        }

        // 发货时间为空时，给一个可演示的默认基线，避免出现“全空轨迹”
        LocalDateTime baseTime = (shipTime != null) ? shipTime : now.minusHours(3);

        boolean fastForward = logisticsProperties.getMock().isFastForward();
        List<Integer> minutePlan = resolveMinutePlan(fastForward);

        int hash = stablePositiveHash(trackingNo);
        // 快进模式抖动小一点，常规模式抖动大一点
        int jitter = fastForward ? (hash % 2) : (hash % 11);

        // 根据 trackingNo 稳定生成路线，保证同一运单每次都一致
        String[] route = buildRoute(shippingCompany, hash);

        List<StageTemplate> stages = Arrays.asList(
                new StageTemplate("已揽件"),
                new StageTemplate("运输中"),
                new StageTemplate("派送中"),
                new StageTemplate("已签收")
        );

        List<LogisticsTrackNode> nodes = new ArrayList<>();

        for (int i = 0; i < stages.size(); i++) {
            // 第一阶段固定 minute=0，确保刚发货时也至少能看到“已揽件”
            int minuteOffset = minutePlan.get(i) + (i == 0 ? 0 : jitter);
            LocalDateTime nodeTime = baseTime.plusMinutes(minuteOffset);

            // 未来节点不返回，模拟“物流逐步推进”
            if (nodeTime.isAfter(now)) {
                break;
            }

            LogisticsTrackNode node = new LogisticsTrackNode();
            node.setTime(nodeTime);
            node.setStatus(stages.get(i).status);
            node.setLocation(route[Math.min(i, route.length - 1)]);
            nodes.add(node);
        }

        // 兜底：极端情况下也保证至少有一条节点，避免前端空白
        if (nodes.isEmpty()) {
            LogisticsTrackNode first = new LogisticsTrackNode();
            first.setStatus(stages.get(0).status);
            first.setLocation(route[0]);
            first.setTime(baseTime.isAfter(now) ? now.minusSeconds(30) : baseTime);
            nodes.add(first);
        }

        result.setTrace(nodes);
        return result;
    }

    /**
     * 解析“当前时间”：
     * - 未配置 fixedNow：使用真实 now（演示更自然）
     * - 配置 fixedNow：使用固定时间（回归可复现）
     */
    private LocalDateTime resolveNow() {
        String fixedNow = logisticsProperties.getMock().getFixedNow();
        if (fixedNow == null || fixedNow.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        String text = fixedNow.trim();
        try {
            return LocalDateTime.parse(text, FIXED_NOW_FMT_1);
        } catch (DateTimeParseException ignore) {
            // 尝试 ISO_LOCAL_DATE_TIME
        }
        try {
            return LocalDateTime.parse(text, FIXED_NOW_FMT_2);
        } catch (DateTimeParseException ex) {
            log.warn("invalid logistics.mock.fixed-now: {}, fallback to system now", text);
            return LocalDateTime.now();
        }
    }

    /**
     * 解析节点分钟计划。
     * - 快进模式读 fastForwardMinutes
     * - 常规模式读 normalMinutes
     * - 如果配置异常（为空/少于4个/出现负值），回退默认值
     */
    private List<Integer> resolveMinutePlan(boolean fastForward) {
        List<Integer> candidate = fastForward
                ? logisticsProperties.getMock().getFastForwardMinutes()
                : logisticsProperties.getMock().getNormalMinutes();

        List<Integer> defaults = fastForward
                ? Arrays.asList(0, 2, 4, 6)
                : Arrays.asList(0, 30, 120, 240);

        if (candidate == null || candidate.size() < 4) {
            return defaults;
        }
        for (Integer minute : candidate) {
            if (minute == null || minute < 0) {
                return defaults;
            }
        }
        return candidate;
    }

    /**
     * 构造稳定路线。
     * 规则：根据 hash 选出两个城市，拼出“揽收 -> 干线 -> 末端 -> 签收”路径。
     */
    private String[] buildRoute(String shippingCompany, int hash) {
        String carrier = (shippingCompany == null || shippingCompany.trim().isEmpty())
                ? "平台物流"
                : shippingCompany.trim();

        int idx1 = hash % CITY_POOL.length;
        int idx2 = (hash / 3) % CITY_POOL.length;
        if (idx2 == idx1) {
            idx2 = (idx2 + 1) % CITY_POOL.length;
        }

        String cityA = CITY_POOL[idx1];
        String cityB = CITY_POOL[idx2];

        return new String[]{
                carrier + " " + cityA + " 揽收中心",
                carrier + " " + cityA + " 转运中心",
                carrier + " " + cityB + " 末端网点",
                carrier + " " + cityB + " 收件地址"
        };
    }

    /**
     * 生成稳定且非负的 hash 值。
     * 说明：Math.abs(Integer.MIN_VALUE) 仍可能是负数，所以做一次兜底。
     */
    private int stablePositiveHash(String value) {
        int raw = (value == null ? "" : value).hashCode();
        if (raw == Integer.MIN_VALUE) {
            return 0;
        }
        return Math.abs(raw);
    }

    /**
     * 轨迹阶段模板（仅保留状态，地点由 route 动态生成）
     */
    private static class StageTemplate {
        private final String status;

        private StageTemplate(String status) {
            this.status = status;
        }
    }
}
