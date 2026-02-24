package com.demo.service;

/**
 * Day16 - 商品状态审计服务。
 *
 * 设计目标：
 * 1) 统一商品状态审计写入入口，避免业务层到处手写 Mapper 入库。
 * 2) 保证审计字段口径一致（action/operator/before/after/reason/extra）。
 * 3) 作为主事务的一部分执行，失败即回滚主流程（审计完整性优先）。
 */
public interface ProductAuditService {

    /**
     * 记录一条商品状态审计日志。
     *
     * @param productId    商品 ID
     * @param action       动作编码（建议使用 ProductActionType.code）
     * @param operatorId   操作人 ID
     * @param operatorRole 操作人角色（admin/seller/system）
     * @param beforeStatus 变更前状态
     * @param afterStatus  变更后状态
     * @param reasonCode   原因编码（可空）
     * @param reasonText   原因文本（可空）
     * @param extraJson    扩展 JSON（可空）
     */
    void record(Long productId,
                String action,
                Long operatorId,
                String operatorRole,
                String beforeStatus,
                String afterStatus,
                String reasonCode,
                String reasonText,
                String extraJson);
}
