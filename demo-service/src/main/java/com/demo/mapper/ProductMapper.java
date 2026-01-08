package com.demo.mapper;

import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import com.demo.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<Product> getPendingApprovalProducts(@Param("productName") String productName,
                                             @Param("category") String category,
                                             @Param("status") String status);

    Product getProductById(@Param("productId") Long productId);

    void updateProduct(Product product);

    List<Product> getUserProducts(@Param("userId") Long userId,
                                  @Param("status") String status);

    int updateStatusAndReason(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("reason") String reason);

    int insertProduct(Product product);

    List<Product> getMarketProductList(@Param("keyword") String keyword,
                                       @Param("category") String category);

    Product getMarketProductById(@Param("productId") Long productId);


    int softDeleteByOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    int updateStatusAndReasonByOwner(@Param("id") Long id,
                                     @Param("ownerId") Long ownerId,
                                     @Param("status") String status,
                                     @Param("reason") String reason);

    int updateStatusAndReasonIfUnderReview(@Param("productId") Long productId,
                                           @Param("status") String status,
                                           @Param("reason") String reason);


}
