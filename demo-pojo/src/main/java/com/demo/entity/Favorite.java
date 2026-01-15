package com.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("favorites")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    // 明确逻辑删除值，避免不同默认策略导致误判
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
