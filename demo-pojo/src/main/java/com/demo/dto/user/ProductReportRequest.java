package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 买家举报商品请求参数。
 * 对应接口：POST /user/market/products/{productId}/report
 */
@Data
public class ProductReportRequest {
    /** 举报类型（如 misleading_desc）。 */
    @NotBlank(message = "reportType 不能为空")
    @Size(max = 64, message = "reportType 长度不能超过64")
    private String reportType;

    /** 举报描述。 */
    @NotBlank(message = "description 不能为空")
    @Size(max = 500, message = "description 长度不能超过500")
    private String description;

    /** 举报证据 URL 列表（可选）。 */
    private List<String> evidenceUrls;
}

