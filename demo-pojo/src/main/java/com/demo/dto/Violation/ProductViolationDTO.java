package com.demo.dto.Violation;

import java.util.List;

public class ProductViolationDTO {
    private Long productId;
    private String violationType;
    private String description;
    private List<String> evidenceUrls;
    private String punishmentResult;
    private Integer creditScoreChange;

    // Getters and Setters
}
