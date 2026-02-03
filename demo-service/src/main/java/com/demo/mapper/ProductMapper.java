package com.demo.mapper;

import com.demo.dto.seller.SellerProductCountDTO;
import com.demo.dto.statistics.ProductPublishCountDTO;
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

    SellerProductCountDTO countProductsBySellerId(@Param("sellerId") Long sellerId);

    Long countActiveProductsByOwnerId(@Param("ownerId") Long ownerId);

    List<Product> listByIds(@Param("ids") List<Long> ids);

    /**
     * Day13 Step7 - 统计指定日期的商品发布量（按 category 分组）
     */
    List<ProductPublishCountDTO> countProductPublishByDate(@Param("date") java.time.LocalDate date);

}
