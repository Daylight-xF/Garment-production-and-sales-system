package com.garment.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionPlanControllerSecurityTest {

    @Test
    void planListShouldAllowInventoryInUsers() throws NoSuchMethodException {
        PreAuthorize preAuthorize = ProductionPlanController.class
                .getMethod("getPlanList", String.class, String.class, int.class, int.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).contains("INVENTORY_IN");
    }
}
