package com.demo.service.serviceimpl;

import com.demo.entity.ProductStatusAuditLog;
import com.demo.exception.BusinessException;
import com.demo.mapper.ProductStatusAuditLogMapper;
import com.demo.service.ProductAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Day16 - 商品状态审计服务实现。
 *
 * 约束：
 * 1) 仅做“参数归一化 + 审计行写入”，不包含业务状态机判断。
 * 2) 审计写入失败时抛业务异常，交由上层事务统一回滚。
 */
@Service
public class ProductAuditServiceImpl implements ProductAuditService {

    @Autowired
    private ProductStatusAuditLogMapper productStatusAuditLogMapper;

    @Override
    public void record(Long productId,
                       String action,
                       Long operatorId,
                       String operatorRole,
                       String beforeStatus,
                       String afterStatus,
                       String reasonCode,
                       String reasonText,
                       String extraJson) {
        if (productId == null) {
            throw new BusinessException("审计写入失败：productId 不能为空");
        }
        if (action == null || action.trim().isEmpty()) {
            throw new BusinessException("审计写入失败：action 不能为空");
        }
        if (operatorId == null) {
            throw new BusinessException("审计写入失败：operatorId 不能为空");
        }
        if (beforeStatus == null || beforeStatus.trim().isEmpty()) {
            throw new BusinessException("审计写入失败：beforeStatus 不能为空");
        }
        if (afterStatus == null || afterStatus.trim().isEmpty()) {
            throw new BusinessException("审计写入失败：afterStatus 不能为空");
        }

        ProductStatusAuditLog logRow = new ProductStatusAuditLog();
        logRow.setProductId(productId);
        logRow.setAction(action.trim());
        logRow.setOperatorId(operatorId);
        logRow.setOperatorRole((operatorRole == null || operatorRole.trim().isEmpty())
                ? "system"
                : operatorRole.trim());
        logRow.setBeforeStatus(beforeStatus.trim());
        logRow.setAfterStatus(afterStatus.trim());
        logRow.setReasonCode(normalizeBlankToNull(reasonCode));
        logRow.setReasonText(normalizeBlankToNull(reasonText));
        logRow.setExtraJson(normalizeBlankToNull(extraJson));

        int rows = productStatusAuditLogMapper.insert(logRow);
        if (rows != 1) {
            throw new BusinessException("审计写入失败：影响行数异常");
        }
    }

    /**
     * 把空白字符串归一化为 null，避免写入无意义空串。
     */
    private String normalizeBlankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
