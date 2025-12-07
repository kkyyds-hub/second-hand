package com.demo.service.serviceimpl;

import com.demo.dto.base.PageQueryDTO;
import com.demo.entity.Order;
import com.demo.mapper.OrderMapper;
import com.demo.service.OrderService;
import com.demo.vo.BuyerOrderSummary;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public PageInfo<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId) {
        Integer page = pageQueryDTO.getPage();
        Integer size = pageQueryDTO.getSize(); // 或 pageQueryDTO.getPageSize()

        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1) {
            size = 10;
        }

        PageHelper.startPage(page, size);
        List<BuyerOrderSummary> list = orderMapper.listBuyerOrders(currentUserId, pageQueryDTO);
        return new PageInfo<>(list);
    }
    }

