package com.demo.mapper;

import com.demo.entity.ProductViolation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商品违规记录 Mapper。
 */
@Mapper
public interface ProductViolationMapper {

    /**
     * 按商品 ID 查询违规记录列表。
     */
    List<ProductViolation> findByProductId(Long productId);

    /**
     * 新增一条商品违规记录。
     */
    void insert(ProductViolation violation);
}
