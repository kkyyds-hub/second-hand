package com.demo.dto.base;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
public class PageQueryDTO {
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer size = 10;

    private String sortField = "createTime";
    private String sortOrder = "desc";
}