package com.demo.mapper;

import com.demo.dto.seller.SellerProductCountDTO;
import com.demo.dto.statistics.ProductPublishCountDTO;
import com.demo.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 商品数据访问接口。
 */
@Mapper
public interface ProductMapper {

    /**
     * 查询管理端待审核商品列表。
     */
    List<Product> getPendingApprovalProducts(@Param("productName") String productName,
                                             @Param("category") String category,
                                             @Param("status") String status);

    /**
     * 按商品 ID 查询商品。
     */
    Product getProductById(@Param("productId") Long productId);

    /**
     * 更新商品信息。
     */
    void updateProduct(Product product);

    /**
     * 查询用户发布的商品列表。
     */
    List<Product> getUserProducts(@Param("userId") Long userId,
                                  @Param("status") String status);

    /**
     * 按商品 ID 更新状态与原因。
     */
    int updateStatusAndReason(@Param("id") Long id,
                              @Param("status") String status,
                              @Param("reason") String reason);

    /**
     * 新增商品。
     */
    int insertProduct(Product product);

    /**
     * 查询市场商品列表（仅展示可售商品）。
     */
    List<Product> getMarketProductList(@Param("keyword") String keyword,
                                       @Param("category") String category);

    /**
     * 查询市场商品详情（仅展示可售商品）。
     */
    Product getMarketProductById(@Param("productId") Long productId);


    /**
     * 按卖家执行商品软删除。
     */
    int softDeleteByOwner(@Param("id") Long id, @Param("ownerId") Long ownerId);

    /**
     * 按卖家更新商品状态与原因。
     */
    int updateStatusAndReasonByOwner(@Param("id") Long id,
                                     @Param("ownerId") Long ownerId,
                                     @Param("status") String status,
                                     @Param("reason") String reason);

    /**
     * 仅当商品处于待审核时更新状态与原因。
     */
    int updateStatusAndReasonIfUnderReview(@Param("productId") Long productId,
                                           @Param("status") String status,
                                           @Param("reason") String reason);

    /**
     * 统计卖家商品数量分布。
     */
    SellerProductCountDTO countProductsBySellerId(@Param("sellerId") Long sellerId);

    /**
     * 统计卖家活跃商品数量。
     */
    Long countActiveProductsByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 按商品 ID 集合批量查询。
     */
    List<Product> listByIds(@Param("ids") List<Long> ids);

    /**
     * Day13 Step7 - 统计指定日期的商品发布量（按 category 分组）。
     */
    List<ProductPublishCountDTO> countProductPublishByDate(@Param("date") java.time.LocalDate date);

}

