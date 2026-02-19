package com.demo.service.serviceimpl;

import com.demo.mapper.OrderMapper;
import com.demo.mapper.ProductMapper;
import com.demo.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Day13 Step7 - 统计服务实现
 */
@Service
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String DAU_KEY_PREFIX = "dau:";

    /**
     * 统计指定日期 DAU。
     */
    @Override
    public Long countDAU(LocalDate date) {
        // Day13 冻结：DAU 用 Redis 计数（SET 去重）
        // 实际项目可用 user_activity_daily 表
        if (stringRedisTemplate == null) {
            log.warn("Redis 未启用，DAU 统计返回 0");
            return 0L;
        }

        String key = DAU_KEY_PREFIX + date.toString();
        Long size = stringRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }

    /**
     * 统计指定日期商品发布量（总量+按分类）。
     */
    @Override
    public Map<String, Object> countProductPublish(LocalDate date) {
        List<com.demo.dto.statistics.ProductPublishCountDTO> categoryList =
                productMapper.countProductPublishByDate(date);

        Map<String, Object> result = new HashMap<>();
        long total = 0L;
        Map<String, Long> categoryMap = new HashMap<>();

        for (com.demo.dto.statistics.ProductPublishCountDTO row : categoryList) {
            String category = row.getCategory();
            Long count = row.getCount() == null ? 0L : row.getCount();
            categoryMap.put(category != null ? category : "未分类", count);
            total += count;
        }

        result.put("date", date.toString());
        result.put("total", total);
        result.put("byCategory", categoryMap);
        return result;
    }

    /**
     * 统计指定日期订单量与 GMV。
     */
    @Override
    public Map<String, Object> countOrderAndGMV(LocalDate date) {
        com.demo.dto.statistics.OrderGmvStatsDTO stats = orderMapper.countOrderAndGMVByDate(date);
        if (stats == null) {
            stats = new com.demo.dto.statistics.OrderGmvStatsDTO();
            stats.setOrderCount(0L);
            stats.setGmv(java.math.BigDecimal.ZERO);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("orderCount", stats.getOrderCount());
        result.put("gmv", stats.getGmv());
        return result;
    }
}
