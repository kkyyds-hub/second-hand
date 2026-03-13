package com.demo.mapper;

import com.demo.entity.AfterSale;
import com.demo.entity.AfterSaleEvidence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Day13 Step5 - 鍞悗 Mapper
 */
@Mapper
public interface AfterSaleMapper {

    /**
     * 鎻掑叆鍞悗璁板綍
     */
    int insertAfterSale(AfterSale afterSale);

    /**
     * 鎻掑叆鍞悗鍑瘉
     */
    int insertEvidence(AfterSaleEvidence evidence);

    /**
     * 鎵归噺鎻掑叆鍑瘉
     */
    int batchInsertEvidences(@Param("list") List<AfterSaleEvidence> evidences);

    /**
     * 鏍规嵁璁㈠崟 ID 鏌ヨ鍞悗璁板綍锛堜竴鍗曚粎涓€涓級
     */
    AfterSale selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 鏍规嵁 ID 鏌ヨ鍞悗璁板綍
     */
    AfterSale selectById(@Param("id") Long id);

    /**
     * 鍗栧鍝嶅簲锛氭洿鏂扮姸鎬佸拰鍗栧澶囨敞
     */
    int updateSellerDecision(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("sellerRemark") String sellerRemark);

    /**
     * 涔板鎻愪氦绾犵悍锛氭洿鏂扮姸鎬佷负 DISPUTED
     */
    int updateToDisputed(@Param("id") Long id);

    /**
     * 骞冲彴瑁佸喅锛氭洿鏂扮姸鎬佸拰骞冲彴澶囨敞
     */
    int updatePlatformArbitrate(@Param("id") Long id,
                                @Param("status") String status,
                                @Param("platformRemark") String platformRemark);

    /**
     * 鎸夌姸鎬佺粺璁″敭鍚庡崟鏁伴噺銆?     */
    long countByStatus(@Param("status") String status);

    long countByStatusAndDate(@Param("status") String status,
                              @Param("date") java.time.LocalDate date);

    /**
     * 鎸夌姸鎬佹煡璇㈠敭鍚庡崟鍒楄〃锛堢敤浜庡悗鍙板伐浣滃彴灞曠ず锛夈€?     */
    List<AfterSale> selectByStatus(@Param("status") String status,
                                   @Param("limit") int limit);

    /**
     * 鏌ヨ绠＄悊绔籂绾蜂腑蹇冮渶瑕佺殑鏈€杩戝敭鍚庡伐鍗曘€?     */
    List<AfterSale> selectRecentForAudit(@Param("limit") int limit);

    /**
     * 鏌ヨ鍞悗鍑瘉鍒楄〃
     */
    List<AfterSaleEvidence> selectEvidencesByAfterSaleId(@Param("afterSaleId") Long afterSaleId);
}

