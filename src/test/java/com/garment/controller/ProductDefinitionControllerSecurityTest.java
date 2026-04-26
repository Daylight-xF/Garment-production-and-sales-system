package com.garment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDefinitionControllerSecurityTest {

    @Test
    void productDefinitionListShouldAllowInventoryInUsers() throws NoSuchMethodException {
        PreAuthorize preAuthorize = ProductDefinitionController.class
                .getMethod("getProductDefinitionList", String.class, String.class, String.class, int.class, int.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("INVENTORY_IN");
    }
}
