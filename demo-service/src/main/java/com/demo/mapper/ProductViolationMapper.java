package com.demo.mapper;

import com.demo.entity.ProductViolation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 按商品统计有效违规记录总数。
     */
    long countByProductId(@Param("productId") Long productId);

    /**
     * 按商品分页查询有效违规记录。
     */
    List<ProductViolation> findByProductIdPage(@Param("productId") Long productId,
                                               @Param("offset") int offset,
                                               @Param("pageSize") int pageSize);

    /**
     * 新增一条商品违规记录。
     */
    void insert(ProductViolation violation);
}
