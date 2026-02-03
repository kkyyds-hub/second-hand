package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Day13 Step5 - 售后凭证表
 * 表：after_sale_evidences
 */
@Data
@TableName("after_sale_evidences")
public class AfterSaleEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long afterSaleId;

    /**
     * 图片 URL
     */
    private String imageUrl;

    /**
     * 排序
     */
    private Integer sort;

    private LocalDateTime createTime;
}
