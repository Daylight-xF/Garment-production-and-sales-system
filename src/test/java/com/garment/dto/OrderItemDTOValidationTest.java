package com.garment.dto;

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAllowProductCodeNameColorAndSizeWhenProductIdIsBlank() {
        OrderItemDTO item = OrderItemDTO.builder()
                .productId("")
                .productCode("N2")
                .productName("Dress")
                .color("Pink")
                .size("M")
                .quantity(1)
                .unitPrice(99.0)
                .amount(99.0)
                .build();

        Set<ConstraintViolation<OrderItemDTO>> violations = validator.validate(item);

        assertThat(violations)
                .noneMatch(violation -> "productId".equals(violation.getPropertyPath().toString()));
    }
}
