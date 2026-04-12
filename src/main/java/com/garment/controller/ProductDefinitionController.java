package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.ProductDefinitionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product-definition")
public class ProductDefinitionController {

    private final ProductDefinitionService productDefinitionService;

    public ProductDefinitionController(ProductDefinitionService productDefinitionService) {
        this.productDefinitionService = productDefinitionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_READ')")
    public Result<Map<String, Object>> getProductDefinitionList(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ProductDefinitionVO> pageResult = productDefinitionService.getProductDefinitionList(name, category, status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageResult.getContent());
        result.put("total", pageResult.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_READ')")
    public Result<ProductDefinitionVO> getProductDefinitionById(@PathVariable String id) {
        ProductDefinitionVO vo = productDefinitionService.getProductDefinitionById(id);
        return Result.success(vo);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_CREATE')")
    public Result<ProductDefinitionVO> createProductDefinition(@Valid @RequestBody ProductDefinitionCreateRequest request) {
        ProductDefinitionVO vo = productDefinitionService.createProductDefinition(request);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_UPDATE')")
    public Result<ProductDefinitionVO> updateProductDefinition(@PathVariable String id,
                                                                 @Valid @RequestBody ProductDefinitionUpdateRequest request) {
        ProductDefinitionVO vo = productDefinitionService.updateProductDefinition(id, request);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_DELETE')")
    public Result<Void> deleteProductDefinition(@PathVariable String id) {
        productDefinitionService.deleteProductDefinition(id);
        return Result.success();
    }
}
