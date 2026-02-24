package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户收藏关系实体。
 */
@Data
@TableName("favorites")
public class Favorite extends BaseAuditEntity {

    /** 主键 ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 商品 ID。 */
    private Long productId;

    /** 逻辑删除标记（0=未删除，1=已删除）。 */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

}
