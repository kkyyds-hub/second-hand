package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 查询当前用户发布商品的分页请求参数
 */
@Data
public class UserProductQueryDTO extends PageQueryDTO {

    /** 用户 ID，必填 */
    private Long userId;

    /** 商品状态，允许为空表示全部 */
    private String status;
}