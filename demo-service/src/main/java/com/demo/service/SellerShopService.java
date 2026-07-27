package com.demo.service;

import com.demo.dto.user.SellerShopProductQueryDTO;
import com.demo.dto.user.SellerShopProfileDTO;
import com.demo.dto.user.SellerShopProductDTO;
import com.demo.result.PageResult;

/**
 * 卖家小店公开页面服务接口。
 */
public interface SellerShopService {

    /**
     * 查询卖家小店概览信息。
     *
     * @param sellerId 卖家用户 ID
     * @return 小店概览（含统计与安全字段）
     */
    SellerShopProfileDTO getShopProfile(Long sellerId);

    /**
     * 分页查询卖家小店公开商品。
     *
     * @param sellerId 卖家用户 ID
     * @param queryDTO 查询参数（状态、分页、排除商品）
     * @return 商品分页结果
     */
    PageResult<SellerShopProductDTO> getShopProducts(Long sellerId, SellerShopProductQueryDTO queryDTO);
}
