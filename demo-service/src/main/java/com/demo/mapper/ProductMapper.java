package com.demo.mapper;

import com.demo.entity.Product;
import org.apache.ibatis.annotations.Mapper;

import javax.validation.constraints.NotNull;
import java.util.List;

@Mapper
public interface ProductMapper {
    List<Product> getPendingApprovalProducts(String productName, String category, String status);

    Product getProductById(Long productId);

    void updateProduct(Product product);

    List<Product> getUserProducts(@NotNull(message = "用户ID不能为空") Long userId, String status);
}
