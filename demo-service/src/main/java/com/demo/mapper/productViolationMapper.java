package com.demo.mapper;

import com.demo.entity.ProductViolation;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface productViolationMapper {
    List<ProductViolation> findByProductId(Long productId);

    void insert(ProductViolation violation);
}
