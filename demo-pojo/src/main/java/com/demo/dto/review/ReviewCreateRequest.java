package com.demo.dto.review;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class ReviewCreateRequest {

    @NotNull(message = "orderId 不能为空")
    @Min(value = 1, message = "orderId 必须大于0")
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分必须为1~5")
    @Max(value = 5, message = "评分必须为1~5")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    private String content;


    /**
     * 匿名评价：true/false（Day12 边界：YES，必须实现）
     */
    @NotNull(message = "isAnonymous 不能为空")
    private Boolean isAnonymous;
}
