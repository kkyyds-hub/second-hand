package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductUpdateRequest {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private BigDecimal price;
    private List<String> images;

}
