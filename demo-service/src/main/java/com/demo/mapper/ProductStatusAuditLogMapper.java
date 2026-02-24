package com.demo.mapper;

import com.demo.entity.ProductStatusAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品状态审计日志 Mapper。
 */
@Mapper
public interface ProductStatusAuditLogMapper {

    /**
     * 新增一条商品状态审计日志。
     */
    int insert(ProductStatusAuditLog log);
}

