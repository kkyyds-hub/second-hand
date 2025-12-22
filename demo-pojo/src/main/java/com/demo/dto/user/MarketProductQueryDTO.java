package com.demo.dto.user;

import com.demo.dto.base.PageQueryDTO;
import lombok.Data;

@Data
public class MarketProductQueryDTO extends PageQueryDTO {
    private String keyword;
    private String category;
}
