package com.demo.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 买家举报商品响应。
 */
@Data
@AllArgsConstructor
public class ProductReportResponse {
    /** 举报工单号。 */
    private String ticketNo;
}

