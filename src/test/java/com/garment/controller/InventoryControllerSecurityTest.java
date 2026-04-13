package com.garment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryControllerSecurityTest {

    @Test
    void finishedProductsListShouldAllowOrderCreationUsers() throws NoSuchMethodException {
        PreAuthorize preAuthorize = InventoryController.class
                .getMethod("getFinishedProductList", String.class, String.class, int.class, int.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("ORDER_CREATE");
    }
}
