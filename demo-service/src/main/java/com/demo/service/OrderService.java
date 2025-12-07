package com.demo.service;

import com.demo.dto.base.PageQueryDTO;
import com.demo.entity.Order;
import com.demo.vo.BuyerOrderSummary;
import com.github.pagehelper.PageInfo;

public interface OrderService {

    PageInfo<BuyerOrderSummary> buy(PageQueryDTO pageQueryDTO, Long currentUserId);
}
