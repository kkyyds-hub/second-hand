package com.demo.mapper;

import com.demo.entity.ProductReportTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品举报工单 Mapper。
 */
@Mapper
public interface ProductReportTicketMapper {

    /**
     * 新增举报工单。
     */
    int insert(ProductReportTicket ticket);

    /**
     * 按工单号查询举报工单。
     */
    ProductReportTicket selectByTicketNo(@Param("ticketNo") String ticketNo);

    /**
     * 仅当工单处于 PENDING 时执行处理更新（条件更新，保证并发安全）。
     */
    int resolveIfPending(@Param("ticketNo") String ticketNo,
                         @Param("resolverId") Long resolverId,
                         @Param("resolveAction") String resolveAction,
                         @Param("resolveRemark") String resolveRemark,
                         @Param("targetStatus") String targetStatus);
}

