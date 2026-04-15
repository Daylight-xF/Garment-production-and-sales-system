package com.garment.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionConstantsTest {

    @Test
    void productionManagerShouldBeAbleToReadProductDefinitions() {
        assertThat(PermissionConstants.PRODUCTION_MANAGER_PERMISSIONS)
                .contains(PermissionConstants.PRODUCT_DEFINITION_READ);
    }
}
