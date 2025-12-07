package com.demo.mapper;

import com.demo.dto.base.PageQueryDTO;
import com.demo.vo.BuyerOrderSummary;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderMapper {
    List<BuyerOrderSummary> listBuyerOrders(Long userId, PageQueryDTO pageQueryDTO);
}
