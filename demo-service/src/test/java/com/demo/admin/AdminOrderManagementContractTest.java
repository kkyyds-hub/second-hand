package com.demo.admin;

import com.demo.dto.admin.AdminOrderQueryDTO;
import com.demo.dto.admin.OrderFlagType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminOrderManagementContractTest {

    @Test
    void queryCarriesTheAdminKeywordFilter() {
        AdminOrderQueryDTO query = new AdminOrderQueryDTO();

        query.setKeyword("P4ADMINA_订单");

        assertEquals("P4ADMINA_订单", query.getKeyword());
    }

    @Test
    void orderFlagTypeRejectsValuesOutsideTheAdminVocabulary() {
        assertEquals(OrderFlagType.PAYMENT_RISK, OrderFlagType.fromRequestType("PAYMENT_RISK"));
        assertThrows(IllegalArgumentException.class, () -> OrderFlagType.fromRequestType("suspicious"));
    }
}
