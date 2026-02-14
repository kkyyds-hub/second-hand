package com.demo.service.serviceimpl;

import com.demo.dto.logistics.LogisticsTrackNode;
import com.demo.dto.logistics.LogisticsTrackResult;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.logistics.LogisticsProvider;
import com.demo.logistics.LogisticsProviderFactory;
import com.demo.mapper.OrderMapper;
import com.demo.service.LogisticsService;
import com.demo.vo.order.LogisticsTraceItemVO;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.OrderLogisticsVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单物流服务实现。
 *
 * 实现策略：
 * 1) 先复用订单详情查询做“存在性 + 权限”校验（买家/卖家可见）
 * 2) 始终返回订单快照物流字段
 * 3) 仅在 shipped/completed 状态下查询动态轨迹，避免无效外部调用
 */
@Service
@Slf4j
public class LogisticsServiceImpl implements LogisticsService {

    private final OrderMapper orderMapper;
    private final LogisticsProviderFactory logisticsProviderFactory;

    public LogisticsServiceImpl(OrderMapper orderMapper,
                                LogisticsProviderFactory logisticsProviderFactory) {
        this.orderMapper = orderMapper;
        this.logisticsProviderFactory = logisticsProviderFactory;
    }

    /**
     * 获取订单物流详情。
     *
     * 处理逻辑：
     * - 订单不存在或无权限：抛业务异常
     * - 未发货状态：返回空轨迹
     * - 已发货/已完成：调用 provider 查询轨迹并转换为 VO
     */
    @Override
    public OrderLogisticsVO getOrderLogistics(Long orderId, Long currentUserId) {
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }

        LogisticsProvider provider = logisticsProviderFactory.getProvider();
        OrderLogisticsVO vo = new OrderLogisticsVO();
        vo.setOrderId(detail.getOrderId());
        vo.setStatus(detail.getStatus());
        vo.setShippingCompany(detail.getShippingCompany());
        vo.setTrackingNo(detail.getTrackingNo());
        vo.setShipTime(detail.getShipTime());
        vo.setProvider(provider.getName());
        vo.setTrace(new ArrayList<>());

        OrderStatus orderStatus = OrderStatus.fromDbValue(detail.getStatus());
        if (orderStatus == OrderStatus.SHIPPED || orderStatus == OrderStatus.COMPLETED) {
            // 发货后才查询轨迹，避免 pending/paid 状态下无意义请求
            // 同时做 provider 保护：三方异常时自动回退 mock，不中断接口。
            long start = System.currentTimeMillis();
            try {
                LogisticsTrackResult result = provider.query(
                        detail.getShippingCompany(),
                        detail.getTrackingNo(),
                        detail.getShipTime()
                );
                if (result != null) {
                    vo.setProvider(result.getProvider());
                    vo.setLastSyncTime(result.getLastSyncTime());
                    vo.setTrace(convertTrace(result.getTrace()));
                }
                log.info("logistics query success, orderId={}, provider={}, costMs={}",
                        detail.getOrderId(), provider.getName(), System.currentTimeMillis() - start);
            } catch (Exception ex) {
                // 第一层调用失败时，尝试强制回退 mock，保证页面可展示
                log.warn("logistics query failed, orderId={}, provider={}, fallback=mock",
                        detail.getOrderId(), provider.getName(), ex);
                LogisticsProvider mockProvider = logisticsProviderFactory.getMockProvider();
                try {
                    LogisticsTrackResult fallback = mockProvider.query(
                            detail.getShippingCompany(),
                            detail.getTrackingNo(),
                            detail.getShipTime()
                    );
                    if (fallback != null) {
                        vo.setProvider(fallback.getProvider());
                        vo.setLastSyncTime(fallback.getLastSyncTime());
                        vo.setTrace(convertTrace(fallback.getTrace()));
                    }
                } catch (Exception fallbackEx) {
                    log.error("logistics fallback mock failed, orderId={}", detail.getOrderId(), fallbackEx);
                }
            }
        }

        return vo;
    }

    /**
     * 将 provider 内部 DTO 转换为前端 VO。
     */
    private List<LogisticsTraceItemVO> convertTrace(List<LogisticsTrackNode> nodes) {
        List<LogisticsTraceItemVO> trace = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return trace;
        }
        for (LogisticsTrackNode node : nodes) {
            LogisticsTraceItemVO item = new LogisticsTraceItemVO();
            item.setTime(node.getTime());
            item.setLocation(node.getLocation());
            item.setStatus(node.getStatus());
            trace.add(item);
        }
        return trace;
    }
}
