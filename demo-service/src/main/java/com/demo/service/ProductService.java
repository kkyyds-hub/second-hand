package com.demo.service;

import com.demo.dto.user.*;
import com.demo.entity.Product;
import com.demo.entity.ProductViolation;
import com.github.pagehelper.PageInfo;
import com.demo.result.PageResult;

import javax.validation.Valid;
import java.util.List;

/**
 * 商品领域服务接口。
 * 覆盖管理端审核流程、卖家侧商品管理与市场侧商品查询。
 */
public interface ProductService {

    /**
     * 分页查询待审核商品。
     */
    PageResult<ProductDTO> getPendingApprovalProducts(int page, int pageSize, String productName, String category, String status);

    /**
     * 审核通过或驳回商品。
     */
    void approveProduct(Long productId, boolean isApproved, String reason);

    /**
     * 查询商品违规记录。
     */
    List<ProductViolation> getProductViolations(Long productId);

    /**
     * 新增商品违规记录。
     */
    void addProductViolation(ProductViolation violation);

    /**
     * 通过管理端或系统流程直接更新商品状态。
     */
    void updateProductStatus(Long productId, String status);

    /**
     * 分页查询当前用户商品列表。
     */
    PageResult<Product> getUserProducts(UserProductQueryDTO queryDTO);

    /**
     * 按商品 ID 查询商品详情。
     */
    ProductDetailDTO getProductDetail(Long productId);

    /**
     * 卖家修改自己名下商品。
     */
    ProductDetailDTO updateMyProduct(Long currentUserId, Long productId, @Valid ProductUpdateRequest request);

    /**
     * 下架当前用户商品。
     */
    void offShelfProductStatus(Long currentUserId, Long productId);

    /**
     * 卖家创建商品。
     */
    ProductDetailDTO createProduct(Long currentUserId, ProductCreateRequest request);

    /**
     * 面向买家侧分页查询市场商品。
     */
    PageResult<MarketProductSummaryDTO> getMarketProductList(MarketProductQueryDTO queryDTO);

    /**
     * 面向买家侧查询市场商品详情。
     */
    MarketProductDetailDTO getMarketProductDetail(Long productId);

    /**
     * 删除当前用户商品。
     */
    void deleteMyProduct(Long currentUserId, Long productId);

    /**
     * 将驳回或下架商品重新提交到待审核状态。
     */
    ProductDetailDTO resubmitProduct(Long currentUserId, Long productId);

    /**
     * 将审核通过商品上架。
     */
    ProductDetailDTO onShelfProduct(Long currentUserId, Long productId);

    /**
     * 将已上架商品撤回为下架状态。
     */
    ProductDetailDTO withdrawProduct(Long currentUserId, Long productId);
}
