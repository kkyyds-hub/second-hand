package com.demo.service;

import com.demo.dto.admin.AdminAuditQueryDTO;
import com.demo.vo.admin.AdminAuditOverviewVO;

/**
 * 管理端纠纷与违规页聚合服务。
 * 负责把售后纠纷、商品举报、违规线索整理成页面可直接消费的数据结构。
 */
public interface AdminAuditService {

    /**
     * 查询纠纷与违规页总览数据。
     */
    AdminAuditOverviewVO getOverview(AdminAuditQueryDTO queryDTO);
}
