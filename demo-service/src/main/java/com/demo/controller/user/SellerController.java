package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.SellerSummaryDTO;
import com.demo.result.Result;
import com.demo.service.SellerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户卖家中心相关接口。
 */
@RestController
@RequestMapping("/user/seller")
@Slf4j
public class SellerController {
    
    @Autowired
    private SellerService sellerService;

    /**
     * 查询当前卖家的统计摘要信息。
     */
    @GetMapping("/summary")
    public Result<SellerSummaryDTO> getSummary() {
        log.info("获取卖家统计");
        Long currentUserId = BaseContext.getCurrentId();
        SellerSummaryDTO summary = sellerService.getSummary(currentUserId);
        return Result.success(summary);
    }
}
