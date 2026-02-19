package com.demo.product;

import com.demo.enumeration.ProductStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Step1 回归：商品状态入参标准化口径
 */
public class ProductStatusNormalizationTest {

    @Test
    void shouldNormalizeChineseAliasToDbValue() {
        Assertions.assertEquals("under_review", ProductStatus.normalizeToDbValue("待审核"));
        Assertions.assertEquals("under_review", ProductStatus.normalizeToDbValue("审核中"));
        Assertions.assertEquals("on_sale", ProductStatus.normalizeToDbValue("在售"));
        Assertions.assertEquals("off_shelf", ProductStatus.normalizeToDbValue("下架"));
        Assertions.assertEquals("sold", ProductStatus.normalizeToDbValue("已售"));
    }

    @Test
    void shouldNormalizeDbValueCaseInsensitive() {
        Assertions.assertEquals("under_review", ProductStatus.normalizeToDbValue("UNDER_REVIEW"));
        Assertions.assertEquals("on_sale", ProductStatus.normalizeToDbValue("on_sale"));
    }

    @Test
    void shouldRejectInvalidStatus() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> ProductStatus.normalizeToDbValue("bad_status"));
    }
}
