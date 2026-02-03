package com.demo.mapper;

import com.demo.entity.AfterSale;
import com.demo.entity.AfterSaleEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Day13 Step5 - 售后 Mapper
 */
@Mapper
public interface AfterSaleMapper {

    /**
     * 插入售后记录
     */
    int insertAfterSale(AfterSale afterSale);

    /**
     * 插入售后凭证
     */
    int insertEvidence(AfterSaleEvidence evidence);

    /**
     * 批量插入凭证
     */
    int batchInsertEvidences(@Param("list") List<AfterSaleEvidence> evidences);

    /**
     * 根据订单 ID 查询售后记录（一单仅一个）
     */
    AfterSale selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据 ID 查询售后记录
     */
    AfterSale selectById(@Param("id") Long id);

    /**
     * 卖家响应：更新状态和卖家备注
     */
    int updateSellerDecision(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("sellerRemark") String sellerRemark);

    /**
     * 买家提交纠纷：更新状态为 DISPUTED
     */
    int updateToDisputed(@Param("id") Long id);

    /**
     * 平台裁决：更新状态和平台备注
     */
    int updatePlatformArbitrate(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("platformRemark") String platformRemark);

    /**
     * 查询售后凭证列表
     */
    List<AfterSaleEvidence> selectEvidencesByAfterSaleId(@Param("afterSaleId") Long afterSaleId);
}
