package com.demo.service.serviceimpl;

import com.demo.dto.base.PageQueryDTO;
import com.demo.dto.user.ShipOrderRequest;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.result.PageResult;
import com.demo.service.OrderService;
import com.demo.vo.order.BuyerOrderSummary;
import com.demo.vo.order.OrderDetail;
import com.demo.vo.order.SellerOrderSummary;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public PageResult<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId) {
        pageValidated(pageQueryDTO);
        List<BuyerOrderSummary> list = orderMapper.listBuyerOrders(currentUserId, pageQueryDTO);
        PageInfo<BuyerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }

    @Override
    public PageResult<SellerOrderSummary> getSellOrder(PageQueryDTO dto, Long uid) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());

        List<SellerOrderSummary> list = orderMapper.listSellerOrders(uid, dto);

        PageInfo<SellerOrderSummary> pageInfo = new PageInfo<>(list);
        return new PageResult<>(
                pageInfo.getList(),
                pageInfo.getTotal(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize()
        );
    }


    @Override
    public OrderDetail getOrderDetail(Long orderId , Long currentUserId) {
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }
        return detail;
    }

    @Override
    public void shipOrder(Long orderId, ShipOrderRequest request, Long currentUserId) {
        // 1. 先查订单详情（同时校验与当前用户有关）
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }

        // 2. 额外校验：只能卖家发货
        if (!Objects.equals(detail.getSellerId(), currentUserId)) {
            throw new BusinessException("只有卖家本人可以发货");
        }

        // 3. 用枚举校验状态
        OrderStatus currentStatus = OrderStatus.fromDbValue(detail.getStatus());
        if (currentStatus == null) {
            throw new BusinessException("订单状态异常");
        }
        if (currentStatus != OrderStatus.PAID) {
            throw new BusinessException("订单当前状态不允许发货，只能对已付款订单发货");
        }

        // 4. 组装 Order 作为更新入参（只放 orders 表需要的字段）
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setShippingCompany(request.getShippingCompany());
        orderToUpdate.setTrackingNo(request.getTrackingNo());
        orderToUpdate.setStatus(OrderStatus.SHIPPED.getDbValue());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        // 5. 执行更新 + 乐观校验
        int rows = orderMapper.updateForShipping(orderToUpdate);
        if (rows == 0) {
            throw new BusinessException("发货失败，订单状态可能已变更");
        }
    }

    @Override
    public void confirmOrder(Long orderId, Long currentUserId) {
        // 1. 查询订单详情（买家/卖家都能查到）
        OrderDetail detail = orderMapper.getOrderDetail(orderId, currentUserId);
        if (detail == null) {
            throw new BusinessException("订单不存在或无权查看该订单");
        }

        // 2. 必须是买家本人才能确认收货
        if (!Objects.equals(detail.getBuyerId(), currentUserId)) {
            throw new BusinessException("只有买家本人可以确认收货");
        }

        // 3. 状态校验：只能从 shipped -> completed
        OrderStatus currentStatus = OrderStatus.fromDbValue(detail.getStatus());
        if (currentStatus == null) {
            throw new BusinessException("订单状态异常");
        }
        if (currentStatus != OrderStatus.SHIPPED) {
            throw new BusinessException("订单当前状态不允许确认收货，只能对已发货订单确认收货");
        }

        // 4. 组装 Order 做更新
        Order orderToUpdate = new Order();
        orderToUpdate.setId(detail.getOrderId());
        orderToUpdate.setStatus(OrderStatus.COMPLETED.getDbValue());
        orderToUpdate.setCompleteTime(LocalDateTime.now());
        orderToUpdate.setUpdateTime(LocalDateTime.now());

        int rows = orderMapper.updateForConfirm(orderToUpdate);
        if (rows == 0) {
            throw new BusinessException("确认收货失败，订单状态可能已变更");
        }
    }


    private void pageValidated(PageQueryDTO pageQueryDTO) {
        Integer page = pageQueryDTO.getPage();
        Integer size = pageQueryDTO.getPageSize();
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }
        PageHelper.startPage(page, size);
    }
}

