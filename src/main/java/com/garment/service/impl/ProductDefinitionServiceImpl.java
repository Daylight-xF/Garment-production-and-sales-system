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


        /**
         * 查询产品定义列表，支持名称、分类、状态筛选及分页
         * <p>
         * 该方法使用MongoDB动态查询构建器，根据条件筛选产品定义记录。
         * 查询结果按分页参数返回，支持模糊查询和精确匹配。
         * </p>
         *
         * @param name 产品名称，支持模糊查询（不区分大小写）
         * @param category 产品分类，精确匹配
         * @param status 产品状态，精确匹配（如“启用”、“禁用”）
         * @param pageable 分页参数，包含页码、每页大小和排序信息
         * @return 分页的产品定义视图对象列表
         */
        @Override
        public Page<ProductDefinitionVO> getProductDefinitionList(String name, String category, String status, Pageable pageable) {
            Query query = new Query();

            // 添加产品名称模糊查询条件
            if (name != null && !name.trim().isEmpty()) {
                query.addCriteria(Criteria.where("productName").regex(Pattern.quote(name.trim()), "i"));
            }

            // 添加产品分类精确查询条件
            if (category != null && !category.trim().isEmpty()) {
                query.addCriteria(Criteria.where("category").is(category));
            }

            // 添加产品状态精确查询条件
            if (status != null && !status.trim().isEmpty()) {
                query.addCriteria(Criteria.where("status").is(status));
            }

            // 计算符合条件的总记录数
            long total = mongoTemplate.count(query, ProductDefinition.class);
            query.with(pageable);

            // 执行查询并转换为视图对象
            List<ProductDefinition> definitions = mongoTemplate.find(query, ProductDefinition.class);
            List<ProductDefinitionVO> voList = definitions.stream()
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            return new PageImpl<>(voList, pageable, total);
        }


        /**
         * 根据ID查询产品定义详情
         * <p>
         * 该方法根据产品定义ID从数据库中查找对应的产品定义记录，
         * 并将其转换为包含完整信息的视图对象返回。
         * </p>
         *
         * @param id 产品定义ID
         * @return 产品定义视图对象，包含产品详细信息和原材料清单
         * @throws BusinessException 如果产品定义不存在时抛出业务异常
         */
        @Override
        public ProductDefinitionVO getProductDefinitionById(String id) {
            // 查找产品定义记录
            ProductDefinition definition = productDefinitionRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("产品定义不存在"));
            // 转换为视图对象
            return convertToVO(definition);
        }
    

        /**
         * 创建产品定义，验证产品编号唯一性并关联原材料信息
         * <p>
         * 该方法用于创建新的产品定义，主要功能包括：
         * 1. 验证产品编号的唯一性
         * 2. 根据原材料ID列表查找对应的原材料信息
         * 3. 构建产品原材料清单，包含数量、单位等详细信息
         * 4. 设置产品默认状态为“启用”
         * 5. 保存产品定义记录
         * </p>
         *
         * @param request 产品定义创建请求参数，包含产品编码、名称、分类、状态、描述、原材料清单等
         * @return 创建后的产品定义视图对象，包含计算后的单位成本
         * @throws BusinessException 如果产品编号已存在或引用的原材料不存在时抛出业务异常
         */
        @Override
        @Transactional
        public ProductDefinitionVO createProductDefinition(ProductDefinitionCreateRequest request) {
            // 验证产品编号唯一性
            if (productDefinitionRepository.existsByProductCode(request.getProductCode())) {
                throw new BusinessException("产品编号已存在");
            }

            // 构建产品原材料清单
            List<ProductDefinition.ProductMaterial> materials = null;
            if (request.getMaterials() != null && !request.getMaterials().isEmpty()) {
                materials = request.getMaterials().stream().map(item -> {
                    // 查找原材料记录
                    RawMaterial material = rawMaterialRepository.findById(item.getMaterialId())
                            .orElseThrow(() -> new BusinessException("原材料不存在: " + item.getMaterialId()));

                    // 构建产品原材料对象
                    ProductDefinition.ProductMaterial pm = new ProductDefinition.ProductMaterial();
                    pm.setMaterialId(material.getId());
                    pm.setMaterialName(material.getName());
                    pm.setMaterialCategory(material.getCategory());
                    pm.setQuantity(item.getQuantity());
                    pm.setUnit(material.getUnit());
                    return pm;
                }).collect(Collectors.toList());
            }

            // 创建产品定义对象
            ProductDefinition definition = new ProductDefinition();
            definition.setProductCode(request.getProductCode());
            definition.setProductName(request.getProductName());
            definition.setCategory(request.getCategory());
            definition.setStatus(request.getStatus() != null ? request.getStatus() : "启用");
            definition.setDescription(request.getDescription());
            definition.setMaterials(materials);

            // 保存产品定义，处理重复键异常
            try {
                ProductDefinition saved = productDefinitionRepository.save(definition);
                return convertToVO(saved);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException("产品编号已存在");
            }
        }


        /**
         * 更新产品定义信息，支持部分字段更新和原材料清单更新
         * <p>
         * 该方法用于更新现有产品定义的详细信息，主要功能包括：
         * 1. 查找并验证产品定义是否存在
         * 2. 更新产品基本信息（名称、分类、状态、描述）
         * 3. 如果提供了原材料清单，则重新构建原材料关联信息
         * 4. 保存更新后的产品定义
         * </p>
         *
         * @param id 产品定义ID
         * @param request 产品定义更新请求参数，包含需要更新的字段（支持部分更新）
         * @return 更新后的产品定义视图对象
         * @throws BusinessException 如果产品定义不存在或引用的原材料不存在时抛出业务异常
         */
        @Override
        @Transactional
        public ProductDefinitionVO updateProductDefinition(String id, ProductDefinitionUpdateRequest request) {
            // 查找产品定义记录
            ProductDefinition definition = productDefinitionRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("产品定义不存在"));

            // 更新产品基本信息
            definition.setProductName(request.getProductName());
            definition.setCategory(request.getCategory());
            definition.setStatus(request.getStatus());
            definition.setDescription(request.getDescription());

            // 如果提供了原材料清单，则更新
            if (request.getMaterials() != null) {
                List<ProductDefinition.ProductMaterial> materials = request.getMaterials().stream().map(item -> {
                    // 查找原材料记录
                    RawMaterial material = rawMaterialRepository.findById(item.getMaterialId())
                            .orElseThrow(() -> new BusinessException("原材料不存在: " + item.getMaterialId()));

                    // 构建产品原材料对象
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

            // 保存更新后的产品定义
            ProductDefinition saved = productDefinitionRepository.save(definition);
            return convertToVO(saved);
        }
    

        /**
         * 删除产品定义
         * <p>
         * 该方法根据ID从数据库中删除产品定义记录。
         * 删除前会先验证产品定义是否存在。
         * </p>
         *
         * @param id 产品定义ID
         * @throws BusinessException 如果产品定义不存在时抛出业务异常
         */
        @Override
        public void deleteProductDefinition(String id) {
            // 验证产品定义是否存在
            if (!productDefinitionRepository.existsById(id)) {
                throw new BusinessException("产品定义不存在");
            }
            // 执行删除操作
            productDefinitionRepository.deleteById(id);
        }


        /**
         * 将产品定义实体转换为视图对象，并计算单位成本
         * <p>
         * 该方法将数据库中的产品定义实体对象转换为前端展示的视图对象，
         * 主要功能包括：
         * 1. 构建原材料映射表，避免重复查询
         * 2. 将产品原材料清单转换为MaterialVO视图对象列表
         * 3. 根据原材料价格和用量计算产品的单位成本
         * 4. 组装完整的ProductDefinitionVO对象
         * </p>
         *
         * @param definition 产品定义实体对象
         * @return 产品定义视图对象，包含原材料清单和单位成本
         */
        private ProductDefinitionVO convertToVO(ProductDefinition definition) {
            // 构建原材料映射表，避免重复查询
            Map<String, RawMaterial> rawMaterialMap = buildRawMaterialMap(definition);
            List<ProductDefinitionVO.MaterialVO> materials = null;
            double unitCost = 0D;

            // 处理原材料清单：转换为MaterialVO并计算单位成本
            if (definition.getMaterials() != null) {
                // 将每个产品原材料转换为MaterialVO
                materials = definition.getMaterials().stream()
                        .map(m -> buildMaterialVO(m, rawMaterialMap))
                        .collect(Collectors.toList());

                // 累加所有原材料的成本，计算单位成本
                unitCost = materials.stream()
                        .mapToDouble(material -> material.getMaterialCost() != null ? material.getMaterialCost() : 0D)
                        .sum();
            }

            // 组装并返回产品定义视图对象
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


        /**
         * 构建产品定义中使用的原材料映射表，避免重复查询
         * <p>
         * 该方法遍历产品定义中的所有原材料项，为每个唯一的原材料ID查询
         * 对应的RawMaterial对象，并构建从原材料ID到原材料对象的映射表。
         * 通过已存在检查避免对同一原材料ID的重复数据库查询。
         * </p>
         *
         * @param definition 产品定义实体对象
         * @return 原材料ID到原材料对象的映射表，如果没有原材料则返回空Map
         */
        private Map<String, RawMaterial> buildRawMaterialMap(ProductDefinition definition) {
            // 如果原材料清单为空，返回空Map
            if (definition.getMaterials() == null || definition.getMaterials().isEmpty()) {
                return Collections.emptyMap();
            }

            // 构建原材料映射表，已存在的ID跳过查询
            Map<String, RawMaterial> rawMaterialMap = new HashMap<>();
            for (ProductDefinition.ProductMaterial material : definition.getMaterials()) {
                // 跳过空的原材料ID或已查询过的ID
                if (material.getMaterialId() == null || rawMaterialMap.containsKey(material.getMaterialId())) {
                    continue;
                }
                // 查询原材料信息并加入映射表
                rawMaterialRepository.findById(material.getMaterialId())
                        .ifPresent(rawMaterial -> rawMaterialMap.put(material.getMaterialId(), rawMaterial));
            }
            return rawMaterialMap;
        }


    /**
     * 构建物料信息VO对象
     * <p>
     * 根据产品定义中的物料信息和原材料数据，计算物料成本和单价，
     * 并组装成用于前端展示的MaterialVO对象。主要处理流程：
     * 1. 从原材料映射表中获取对应的原材料信息
     * 2. 提取原材料单价（不存在则为0）
     * 3. 计算物料总成本（单价 × 数量）
     * 4. 组装MaterialVO对象返回
     * </p>
     *
     * @param material 产品定义中的物料信息，包含物料ID、名称、数量等基础数据
     * @param rawMaterialMap 原材料映射表，key为物料ID，value为对应的原材料详细信息（包含价格）
     * @return ProductDefinitionVO.MaterialVO 组装完成的物料信息VO对象，包含：
     *         - materialId: 物料ID
     *         - materialName: 物料名称
     *         - materialCategory: 物料类别
     *         - quantity: 数量
     *         - unit: 单位
     *         - materialPrice: 单价（从原材料信息中获取，不存在则为0）
     *         - materialCost: 总成本（单价 × 数量）
     */
    private ProductDefinitionVO.MaterialVO buildMaterialVO(ProductDefinition.ProductMaterial material,
                                                           Map<String, RawMaterial> rawMaterialMap) {
        // 从映射表中获取原材料信息
        RawMaterial rawMaterial = rawMaterialMap.get(material.getMaterialId());
        // 获取原材料单价，不存在则为0
        double materialPrice = rawMaterial != null && rawMaterial.getPrice() != null ? rawMaterial.getPrice() : 0D;
        // 获取物料数量，不存在则为0
        double quantity = material.getQuantity() != null ? material.getQuantity() : 0D;
        // 计算物料总成本 = 单价 × 数量
        double materialCost = materialPrice * quantity;

        // 组装并返回MaterialVO对象
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
