package com.demo.controller.user;

import com.demo.dto.user.SellerShopProductDTO;
import com.demo.dto.user.SellerShopProductQueryDTO;
import com.demo.dto.user.SellerShopProfileDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.SellerShopService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Min;

/**
 * 卖家小店公开页面接口。
 * <p>
 * 核心关系：一个卖家对应一个小店，小店名称运行时计算。
 * 小店不新增 shops 表，使用现有用户与商品数据动态聚合。
 */
@RestController
@RequestMapping("/user/shops")
@Validated
@Slf4j
@Api(tags = "卖家小店接口")
@RequiredArgsConstructor
public class SellerShopController {

    private final SellerShopService sellerShopService;

    /**
     * 查询卖家小店概览。
     *
     * @param sellerId 卖家用户 ID
     * @return 小店概览（含统计与安全字段）
     */
    @GetMapping("/{sellerId}")
    public Result<SellerShopProfileDTO> getShopProfile(@PathVariable @Min(1) Long sellerId) {
        log.info("查询卖家小店概览 sellerId={}", sellerId);
        SellerShopProfileDTO profile = sellerShopService.getShopProfile(sellerId);
        return Result.success(profile);
    }

    /**
     * 分页查询卖家小店公开商品。
     * <p>
     * 支持通过 status 参数筛选在售/已售商品，
     * 支持通过 excludeProductId 排除指定商品（用于"该卖家的其他商品"场景）。
     *
     * @param sellerId 卖家用户 ID
     * @param queryDTO 查询参数
     * @return 商品分页结果
     */
    @GetMapping("/{sellerId}/products")
    public Result<PageResult<SellerShopProductDTO>> getShopProducts(
            @PathVariable @Min(1) Long sellerId,
            @Validated SellerShopProductQueryDTO queryDTO) {
        log.info("查询卖家小店商品 sellerId={}, status={}, page={}, pageSize={}, excludeProductId={}",
                sellerId, queryDTO.getStatus(), queryDTO.getPage(), queryDTO.getPageSize(), queryDTO.getExcludeProductId());
        PageResult<SellerShopProductDTO> pageResult = sellerShopService.getShopProducts(sellerId, queryDTO);
        return Result.success(pageResult);
    }
}
