package com.demo.product;

import com.demo.enumeration.ProductActionType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Step1 回归：商品动作口径统一
 */
public class ProductActionTypeTest {

    @Test
    void shouldParseActionCodeCaseInsensitive() {
        Assertions.assertEquals(ProductActionType.APPROVE, ProductActionType.fromCode("approve"));
        Assertions.assertEquals(ProductActionType.FORCE_OFF_SHELF, ProductActionType.fromCode("FORCE_OFF_SHELF"));
    }

    @Test
    void shouldRejectUnknownActionCode() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ProductActionType.fromCode("unknown_action"));
    }
}
