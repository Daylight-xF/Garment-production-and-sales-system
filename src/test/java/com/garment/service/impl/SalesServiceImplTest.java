package com.garment.service.impl;

import com.garment.dto.SalesRecordVO;
import com.garment.model.SalesRecord;
import com.garment.repository.CustomerRepository;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.SalesRecordRepository;
import com.garment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesServiceImplTest {

    @Mock
    private SalesRecordRepository salesRecordRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductDefinitionRepository productDefinitionRepository;

    @InjectMocks
    private SalesServiceImpl salesService;

    @Test
    void getSalesRecordByIdShouldReturnColorAndSizeInItems() {
        SalesRecord record = new SalesRecord();
        record.setId("sales-1");
        record.setCustomerName("星河服饰");
        record.setItems(Arrays.asList(
                new SalesRecord.SalesRecordItem("finished-1", "N1", "T恤", "红色", "M", 2, 88.0, 176.0)
        ));

        when(salesRecordRepository.findById("sales-1")).thenReturn(Optional.of(record));

        SalesRecordVO result = salesService.getSalesRecordById("sales-1");

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getColor()).isEqualTo("红色");
        assertThat(result.getItems().get(0).getSize()).isEqualTo("M");
    }

    @Test
    void querySalesRecordsShouldKeepLegacySingleItemCompatible() {
        SalesRecord legacy = new SalesRecord();
        legacy.setId("legacy-1");
        legacy.setCustomerName("老客户");
        legacy.setProductId("finished-legacy");
        legacy.setProductCode("OLD1");
        legacy.setProductName("老款T恤");
        legacy.setQuantity(1);
        legacy.setUnitPrice(66.0);
        legacy.setAmount(66.0);

        when(salesRecordRepository.findAll()).thenReturn(Arrays.asList(legacy));

        Page<SalesRecordVO> result = salesService.querySalesRecords("", null, null, "", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getItems()).hasSize(1);
        assertThat(result.getContent().get(0).getItems().get(0).getColor()).isNull();
        assertThat(result.getContent().get(0).getItems().get(0).getSize()).isNull();
    }
}
