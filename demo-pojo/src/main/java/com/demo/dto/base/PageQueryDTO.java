package com.demo.dto.base;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
/**
 * PageQueryDTO 业务组件。
 */
public class PageQueryDTO {
    @Min(value = 1, message = "页码不能小于1")
    /** 字段：page。 */
    private Integer page = 1;

    // 新契约字段：pageSize
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer pageSize;

    // 旧字段：size（先保留兼容）
    @Deprecated
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer size = 10;

    /** 状态。 */
    private String status;
    /** 字段：sortField。 */
    private String sortField = "createTime";
    /** 字段：sortOrder。 */
    private String sortOrder = "desc";

    // 统一读取口径：优先 pageSize，其次 size，最后默认 10
    /**
     * 获取分页大小（含默认值处理）。
     */
    public Integer getPageSize() {
        if (pageSize != null) return pageSize;
        if (size != null) return size;
        return 10;
    }
}
