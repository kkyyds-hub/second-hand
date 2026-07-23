package com.demo.dto.user;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bean Validation 契约测试：MarketProductQueryDTO。
 * 不依赖 Spring 上下文，仅使用 javax.validation 校验。
 */
class MarketProductQueryDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private MarketProductQueryDTO createValid() {
        MarketProductQueryDTO dto = new MarketProductQueryDTO();
        dto.setPage(1);
        dto.setPageSize(12);
        return dto;
    }

    @Test
    @DisplayName("minPrice=null, maxPrice=null → 合法")
    void bothNull_valid() {
        MarketProductQueryDTO dto = createValid();
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "应无校验错误，实际: " + violations);
    }

    @Test
    @DisplayName("minPrice=0 → 合法")
    void minPriceZero_valid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(BigDecimal.ZERO);
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "应无校验错误，实际: " + violations);
    }

    @Test
    @DisplayName("maxPrice=0 → 合法")
    void maxPriceZero_valid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMaxPrice(BigDecimal.ZERO);
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "应无校验错误，实际: " + violations);
    }

    @Test
    @DisplayName("minPrice=199, maxPrice=199 → 合法（相等边界）")
    void equalPrices_valid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("199"));
        dto.setMaxPrice(new BigDecimal("199"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "应无校验错误，实际: " + violations);
    }

    @Test
    @DisplayName("minPrice=199, maxPrice=300 → 合法（正常区间）")
    void normalRange_valid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("199"));
        dto.setMaxPrice(new BigDecimal("300"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "应无校验错误，实际: " + violations);
    }

    @Test
    @DisplayName("minPrice=-0.01 → 非法")
    void minPriceNegative_invalid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("-0.01"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "应有校验错误");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("minPrice")),
                "应包含 minPrice 错误");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("0")),
                "错误信息应提及 0");
    }

    @Test
    @DisplayName("maxPrice=-0.01 → 非法")
    void maxPriceNegative_invalid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMaxPrice(new BigDecimal("-0.01"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "应有校验错误");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxPrice")),
                "应包含 maxPrice 错误");
    }

    @Test
    @DisplayName("minPrice=300, maxPrice=200 → 非法（区间反转）")
    void reversedRange_invalid() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("300"));
        dto.setMaxPrice(new BigDecimal("200"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "应有校验错误");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("最低价")),
                "应包含最低价相关错误信息");
    }

    @Test
    @DisplayName("错误信息与生产 DTO 定义一致：最低价不能小于 0")
    void minPriceMessageMatches() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("-1"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("minPrice"))
                        .anyMatch(v -> v.getMessage().equals("最低价不能小于 0")),
                "minPrice 错误信息应为 '最低价不能小于 0'");
    }

    @Test
    @DisplayName("错误信息与生产 DTO 定义一致：最高价不能小于 0")
    void maxPriceMessageMatches() {
        MarketProductQueryDTO dto = createValid();
        dto.setMaxPrice(new BigDecimal("-1"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream()
                        .filter(v -> v.getPropertyPath().toString().equals("maxPrice"))
                        .anyMatch(v -> v.getMessage().equals("最高价不能小于 0")),
                "maxPrice 错误信息应为 '最高价不能小于 0'");
    }

    @Test
    @DisplayName("错误信息与生产 DTO 定义一致：最低价不能大于最高价")
    void rangeMessageMatches() {
        MarketProductQueryDTO dto = createValid();
        dto.setMinPrice(new BigDecimal("500"));
        dto.setMaxPrice(new BigDecimal("100"));
        Set<ConstraintViolation<MarketProductQueryDTO>> violations = validator.validate(dto);
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("最低价不能大于最高价")),
                "区间错误信息应为 '最低价不能大于最高价'");
    }
}
