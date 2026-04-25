package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.ProductDefinition;
import com.garment.model.RawMaterial;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.RawMaterialRepository;
import com.garment.service.ProductDefinitionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductDefinitionServiceImpl implements ProductDefinitionService {

    private final ProductDefinitionRepository productDefinitionRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final MongoTemplate mongoTemplate;

    public ProductDefinitionServiceImpl(ProductDefinitionRepository productDefinitionRepository,
                                        RawMaterialRepository rawMaterialRepository,
                                        MongoTemplate mongoTemplate) {
        this.productDefinitionRepository = productDefinitionRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<ProductDefinitionVO> getProductDefinitionList(String name, String category, String status, Pageable pageable) {
        Query query = new Query();

        if (name != null && !name.trim().isEmpty()) {
            query.addCriteria(Criteria.where("productName").regex(Pattern.quote(name.trim()), "i"));
        }

        if (category != null && !category.trim().isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }

        if (status != null && !status.trim().isEmpty()) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        long total = mongoTemplate.count(query, ProductDefinition.class);
        query.with(pageable);

        List<ProductDefinition> definitions = mongoTemplate.find(query, ProductDefinition.class);
        List<ProductDefinitionVO> voList = definitions.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, total);
    }

    @Override
    public ProductDefinitionVO getProductDefinitionById(String id) {
        ProductDefinition definition = productDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("产品定义不存在"));
        return convertToVO(definition);
    }

    @Override
    @Transactional
    public ProductDefinitionVO createProductDefinition(ProductDefinitionCreateRequest request) {
        if (productDefinitionRepository.existsByProductCode(request.getProductCode())) {
            throw new BusinessException("产品编号已存在");
        }

        List<ProductDefinition.ProductMaterial> materials = null;
        if (request.getMaterials() != null && !request.getMaterials().isEmpty()) {
            materials = request.getMaterials().stream().map(item -> {
                RawMaterial material = rawMaterialRepository.findById(item.getMaterialId())
                        .orElseThrow(() -> new BusinessException("原材料不存在: " + item.getMaterialId()));

                ProductDefinition.ProductMaterial pm = new ProductDefinition.ProductMaterial();
                pm.setMaterialId(material.getId());
                pm.setMaterialName(material.getName());
                pm.setMaterialCategory(material.getCategory());
                pm.setQuantity(item.getQuantity());
                pm.setUnit(material.getUnit());
                return pm;
            }).collect(Collectors.toList());
        }

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode(request.getProductCode());
        definition.setProductName(request.getProductName());
        definition.setCategory(request.getCategory());
        definition.setStatus(request.getStatus() != null ? request.getStatus() : "启用");
        definition.setDescription(request.getDescription());
        definition.setMaterials(materials);

        try {
            ProductDefinition saved = productDefinitionRepository.save(definition);
            return convertToVO(saved);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("产品编号已存在");
        }
    }

    @Override
    @Transactional
    public ProductDefinitionVO updateProductDefinition(String id, ProductDefinitionUpdateRequest request) {
        ProductDefinition definition = productDefinitionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("产品定义不存在"));

        definition.setProductName(request.getProductName());
        definition.setCategory(request.getCategory());
        definition.setStatus(request.getStatus());
        definition.setDescription(request.getDescription());

        if (request.getMaterials() != null) {
            List<ProductDefinition.ProductMaterial> materials = request.getMaterials().stream().map(item -> {
                RawMaterial material = rawMaterialRepository.findById(item.getMaterialId())
                        .orElseThrow(() -> new BusinessException("原材料不存在: " + item.getMaterialId()));

                ProductDefinition.ProductMaterial pm = new ProductDefinition.ProductMaterial();
                pm.setMaterialId(material.getId());
                pm.setMaterialName(material.getName());
                pm.setMaterialCategory(material.getCategory());
                pm.setQuantity(item.getQuantity());
                pm.setUnit(material.getUnit());
                return pm;
            }).collect(Collectors.toList());
            definition.setMaterials(materials);
        }

        ProductDefinition saved = productDefinitionRepository.save(definition);
        return convertToVO(saved);
    }

    @Override
    public void deleteProductDefinition(String id) {
        if (!productDefinitionRepository.existsById(id)) {
            throw new BusinessException("产品定义不存在");
        }
        productDefinitionRepository.deleteById(id);
    }

    private ProductDefinitionVO convertToVO(ProductDefinition definition) {
        Map<String, RawMaterial> rawMaterialMap = buildRawMaterialMap(definition);
        List<ProductDefinitionVO.MaterialVO> materials = null;
        double unitCost = 0D;

        if (definition.getMaterials() != null) {
            materials = definition.getMaterials().stream()
                    .map(m -> buildMaterialVO(m, rawMaterialMap))
                    .collect(Collectors.toList());

            unitCost = materials.stream()
                    .mapToDouble(material -> material.getMaterialCost() != null ? material.getMaterialCost() : 0D)
                    .sum();
        }

        return ProductDefinitionVO.builder()
                .id(definition.getId())
                .productCode(definition.getProductCode())
                .productName(definition.getProductName())
                .category(definition.getCategory())
                .status(definition.getStatus())
                .description(definition.getDescription())
                .materials(materials)
                .unitCost(unitCost)
                .createTime(definition.getCreateTime())
                .updateTime(definition.getUpdateTime())
                .build();
    }

    private Map<String, RawMaterial> buildRawMaterialMap(ProductDefinition definition) {
        if (definition.getMaterials() == null || definition.getMaterials().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, RawMaterial> rawMaterialMap = new HashMap<>();
        for (ProductDefinition.ProductMaterial material : definition.getMaterials()) {
            if (material.getMaterialId() == null || rawMaterialMap.containsKey(material.getMaterialId())) {
                continue;
            }
            rawMaterialRepository.findById(material.getMaterialId())
                    .ifPresent(rawMaterial -> rawMaterialMap.put(material.getMaterialId(), rawMaterial));
        }
        return rawMaterialMap;
    }

    private ProductDefinitionVO.MaterialVO buildMaterialVO(ProductDefinition.ProductMaterial material,
                                                           Map<String, RawMaterial> rawMaterialMap) {
        RawMaterial rawMaterial = rawMaterialMap.get(material.getMaterialId());
        double materialPrice = rawMaterial != null && rawMaterial.getPrice() != null ? rawMaterial.getPrice() : 0D;
        double quantity = material.getQuantity() != null ? material.getQuantity() : 0D;
        double materialCost = materialPrice * quantity;

        return ProductDefinitionVO.MaterialVO.builder()
                .materialId(material.getMaterialId())
                .materialName(material.getMaterialName())
                .materialCategory(material.getMaterialCategory())
                .quantity(material.getQuantity())
                .unit(material.getUnit())
                .materialPrice(materialPrice)
                .materialCost(materialCost)
                .build();
    }
}
