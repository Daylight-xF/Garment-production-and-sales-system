package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.*;
import com.garment.repository.*;
import com.garment.service.InventoryService;
import com.garment.service.support.MongoAtomicOpsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 库存业务实现类。
 *
 * <p>负责原材料库存、成品库存、库存出入库记录、库存预警、库位移动以及 FIFO 扣减与回滚等业务处理。</p>
 */
@Slf4j
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    // 原材料仓库，用于维护原材料库存主数据。
    private final RawMaterialRepository rawMaterialRepository;
    // 成品仓库，用于维护成品库存主数据。
    private final FinishedProductRepository finishedProductRepository;
    // 产品定义仓库，用于根据产品编码解析成品动态成本。
    private final ProductDefinitionRepository productDefinitionRepository;
    // 生产计划仓库，用于成品入库时关联和更新生产计划入库数量。
    private final ProductionPlanRepository productionPlanRepository;
    // 库存记录仓库，用于保存入库、出库、回滚等流水记录。
    private final InventoryRecordRepository inventoryRecordRepository;
    // 库存预警仓库，用于创建和处理库存预警记录。
    private final InventoryAlertRepository inventoryAlertRepository;
    // 用户仓库，用于解析库存操作人的真实姓名。
    private final UserRepository userRepository;
    // Mongo 原子操作服务，用于并发安全地调整总库存和处理预警。
    private final MongoAtomicOpsService mongoAtomicOpsService;

    /**
     * 构造库存业务实现类。
     *
     * <p>通过构造器注入库存业务依赖的仓库、用户查询组件和 Mongo 原子操作服务。</p>
     *
     * @param rawMaterialRepository 原材料仓库
     * @param finishedProductRepository 成品仓库
     * @param productDefinitionRepository 产品定义仓库
     * @param productionPlanRepository 生产计划仓库
     * @param inventoryRecordRepository 库存记录仓库
     * @param inventoryAlertRepository 库存预警仓库
     * @param userRepository 用户仓库
     * @param mongoAtomicOpsService Mongo 原子操作服务
     */
    public InventoryServiceImpl(RawMaterialRepository rawMaterialRepository,
                                 FinishedProductRepository finishedProductRepository,
                                 ProductDefinitionRepository productDefinitionRepository,
                                 ProductionPlanRepository productionPlanRepository,
                                 InventoryRecordRepository inventoryRecordRepository,
                                 InventoryAlertRepository inventoryAlertRepository,
                                 UserRepository userRepository,
                                 MongoAtomicOpsService mongoAtomicOpsService) {
        // 保存原材料仓库引用。
        this.rawMaterialRepository = rawMaterialRepository;
        // 保存成品仓库引用。
        this.finishedProductRepository = finishedProductRepository;
        // 保存产品定义仓库引用。
        this.productDefinitionRepository = productDefinitionRepository;
        // 保存生产计划仓库引用。
        this.productionPlanRepository = productionPlanRepository;
        // 保存库存记录仓库引用。
        this.inventoryRecordRepository = inventoryRecordRepository;
        // 保存库存预警仓库引用。
        this.inventoryAlertRepository = inventoryAlertRepository;
        // 保存用户仓库引用。
        this.userRepository = userRepository;
        // 保存 Mongo 原子操作服务引用。
        this.mongoAtomicOpsService = mongoAtomicOpsService;
    }


        /**
         * 查询原材料库存列表，支持名称和分类筛选及分页
         * <p>
         * 该方法从数据库中查询所有原材料记录，并根据名称和分类进行筛选。
         * 筛选结果按分页参数返回，支持模糊查询。
         * </p>
         *
         * @param name 原材料名称，支持模糊查询（包含匹配）
         * @param category 分类名称，精确匹配
         * @param pageable 分页参数，包含页码、每页大小和排序信息
         * @return 分页的原材料视图对象列表
         */
        @Override
        public Page<RawMaterialVO> getRawMaterialList(String name, String category, Pageable pageable) {
            // 获取所有原材料记录
            List<RawMaterial> all = rawMaterialRepository.findAll();
            // 根据名称和分类筛选
            List<RawMaterial> filtered = all.stream()
                    .filter(m -> !StringUtils.hasText(name) || (m.getName() != null && m.getName().contains(name)))
                    .filter(m -> !StringUtils.hasText(category) || category.equals(m.getCategory()))
                    .collect(Collectors.toList());

            // 计算分页范围
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<RawMaterial> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

            // 转换为视图对象
            List<RawMaterialVO> voList = pageContent.stream()
                    .map(this::convertToRawMaterialVO)
                    .collect(Collectors.toList());

            return new PageImpl<>(voList, pageable, filtered.size());
        }


        /**
         * 根据ID查询原材料详情
         * <p>
         * 该方法根据原材料ID从数据库中查找对应的原材料记录，
         * 并将其转换为包含完整信息的视图对象返回。
         * </p>
         *
         * @param id 原材料ID
         * @return 原材料视图对象，包含原材料的详细信息
         * @throws BusinessException 如果原材料不存在时抛出业务异常
         */
        @Override
        public RawMaterialVO getRawMaterialById(String id) {
            // 查找原材料记录
            RawMaterial material = rawMaterialRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));
            return convertToRawMaterialVO(material);
        }


        /**
         * 创建原材料入库记录，初始化库存信息
         * <p>
         * 该方法用于创建新的原材料记录并初始化库存，主要功能包括：
         * 1. 验证初始库存数量的合法性（不能为负数）
         * 2. 创建原材料对象并设置基本信息
         * 3. 如果初始数量大于0且提供了位置信息，则创建位置库存记录
         * 4. 保存原材料记录到数据库
         * </p>
         *
         * @param request 原材料创建请求参数，包含名称、分类、规格、数量、单位、预警阈值、供应商、价格、描述等信息
         * @return 创建后的原材料视图对象
         * @throws BusinessException 如果库存数量小于0时抛出业务异常
         */
        @Override
        public RawMaterialVO createRawMaterial(RawMaterialCreateRequest request) {
            // 获取并验证初始库存数量
            int initialQuantity = request.getQuantity() != null ? request.getQuantity() : 0;
            if (initialQuantity < 0) {
                throw new BusinessException("库存数量不能小于0");
            }

            // 创建原材料对象并设置基本信息
            RawMaterial material = new RawMaterial();
            material.setName(request.getName());
            material.setCategory(request.getCategory());
            material.setSpecification(request.getSpecification());
            material.setUnit(request.getUnit());
            material.setQuantity(initialQuantity);
            material.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 0);
            material.setSupplier(request.getSupplier());

            // 如果初始数量大于0且提供了位置信息，则创建位置库存记录
            List<LocationInfo> locations = new ArrayList<>();
            if (initialQuantity > 0 && StringUtils.hasText(request.getLocation())) {
                LocationInfo info = new LocationInfo();
                info.setLocation(request.getLocation().trim());
                info.setQuantity(initialQuantity);
                info.setCreatedAt(new Date());
                locations.add(info);
            }
            material.setLocations(locations);

            // 设置价格和描述信息
            material.setPrice(request.getPrice());
            material.setDescription(request.getDescription());

            // 保存原材料记录
            RawMaterial saved = rawMaterialRepository.save(material);
            return convertToRawMaterialVO(saved);
        }


        /**
         * 更新原材料信息，支持部分字段更新
         * <p>
         * 该方法用于更新现有原材料的详细信息，主要功能包括：
         * 1. 查找并验证原材料是否存在
         * 2. 根据请求参数选择性更新各个字段
         * 3. 如果提供了位置信息且当前无位置记录，则初始化位置库存
         * 4. 保存更新后的原材料记录
         * </p>
         *
         * @param id 原材料ID
         * @param request 原材料更新请求参数，包含需要更新的字段（支持部分更新）
         * @return 更新后的原材料视图对象
         * @throws BusinessException 如果原材料不存在时抛出业务异常
         */
        @Override
        public RawMaterialVO updateRawMaterial(String id, RawMaterialUpdateRequest request) {
            // 查找原材料记录
            RawMaterial material = rawMaterialRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 选择性更新各个字段
            if (request.getName() != null) {
                material.setName(request.getName());
            }
            if (request.getCategory() != null) {
                material.setCategory(request.getCategory());
            }
            if (request.getSpecification() != null) {
                material.setSpecification(request.getSpecification());
            }
            if (request.getUnit() != null) {
                material.setUnit(request.getUnit());
            }
            // 如果提供了位置信息且当前无位置记录，则初始化位置库存
            if (StringUtils.hasText(request.getLocation())) {
                List<LocationInfo> locations = material.getLocations();
                if (locations == null || locations.isEmpty()) {
                    locations = new ArrayList<>();
                    int currentQuantity = material.getQuantity() != null ? material.getQuantity() : 0;
                    if (currentQuantity > 0) {
                        LocationInfo info = new LocationInfo();
                        info.setLocation(request.getLocation());
                        info.setQuantity(currentQuantity);
                        info.setCreatedAt(new Date());
                        locations.add(info);
                    }
                    material.setLocations(locations);
                }
            }
            if (request.getSupplier() != null) {
                material.setSupplier(request.getSupplier());
            }
            if (request.getPrice() != null) {
                material.setPrice(request.getPrice());
            }
            if (request.getDescription() != null) {
                material.setDescription(request.getDescription());
            }

            // 保存更新后的原材料记录
            RawMaterial saved = rawMaterialRepository.save(material);
            return convertToRawMaterialVO(saved);
        }


        /**
         * 删除原材料记录
         * <p>
         * 该方法根据ID从数据库中删除原材料记录。
         * 删除前会先验证原材料是否存在。
         * </p>
         *
         * @param id 原材料ID
         * @throws BusinessException 如果原材料不存在时抛出业务异常
         */
        @Override
        public void deleteRawMaterial(String id) {
            // 验证原材料是否存在
            if (!rawMaterialRepository.existsById(id)) {
                throw new BusinessException("原材料不存在");
            }
            // 执行删除操作
            rawMaterialRepository.deleteById(id);
        }


        /**
         * 查询成品库存列表，支持关键词和分类筛选及分页
         * <p>
         * 该方法从数据库中查询所有成品记录，并根据关键词和分类进行筛选。
         * 关键词支持批次号或名称的模糊查询。
         * 筛选结果按创建时间降序排列后返回分页数据。
         * </p>
         *
         * @param keyword 搜索关键词，支持批次号或名称模糊查询（包含匹配）
         * @param category 分类名称，精确匹配
         * @param pageable 分页参数，包含页码、每页大小和排序信息
         * @return 分页的成品视图对象列表，按创建时间降序排列
         */
        @Override
        public Page<FinishedProductVO> getFinishedProductList(String keyword, String category, Pageable pageable) {
            // 获取所有成品记录
            List<FinishedProduct> all = finishedProductRepository.findAll();
            // 根据关键词和分类筛选
            List<FinishedProduct> filtered = all.stream()
                    .filter(p -> !StringUtils.hasText(keyword)
                            || (p.getBatchNo() != null && p.getBatchNo().contains(keyword))
                            || (p.getName() != null && p.getName().contains(keyword)))
                    .filter(p -> !StringUtils.hasText(category) || category.equals(p.getCategory()))
                    .collect(Collectors.toList());
            // 按创建时间降序排序
            sortFinishedProductsByCreateTimeDesc(filtered);

            // 计算分页范围
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<FinishedProduct> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

            // 转换为视图对象
            List<FinishedProductVO> voList = pageContent.stream()
                    .map(this::convertToFinishedProductVO)
                    .collect(Collectors.toList());

            return new PageImpl<>(voList, pageable, filtered.size());
        }


        /**
         * 获取成品分类列表，去重后按字母顺序排序
         * <p>
         * 该方法从数据库中获取所有成品记录，提取分类名称，
         * 经过去重和排序后返回分类列表。
         * </p>
         *
         * @return 成品分类名称列表，已去重并按字母顺序排序
         */
        @Override
        public List<String> getFinishedProductCategories() {
            // 获取所有成品，提取分类，过滤空值，去重并排序
            return finishedProductRepository.findAll().stream()
                    .map(FinishedProduct::getCategory)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }


        /**
         * 按创建时间降序排序成品列表，null值排在最后
         * <p>
         * 该方法对成品列表按创建时间进行降序排序，
         * 创建时间为null的记录排在列表末尾。
         * </p>
         *
         * @param products 成品列表
         */
        private void sortFinishedProductsByCreateTimeDesc(List<FinishedProduct> products) {
            // 按创建时间降序排序，null值排在最后
            products.sort((left, right) -> {
                Date leftCreateTime = left.getCreateTime();
                Date rightCreateTime = right.getCreateTime();
                if (leftCreateTime == null && rightCreateTime == null) {
                    return 0;
                }
                if (leftCreateTime == null) {
                    return 1;
                }
                if (rightCreateTime == null) {
                    return -1;
                }
                return rightCreateTime.compareTo(leftCreateTime);
            });
        }


        /**
         * 根据ID查询成品详情
         * <p>
         * 该方法根据成品ID从数据库中查找对应的成品记录，
         * 并将其转换为包含完整信息的视图对象返回。
         * </p>
         *
         * @param id 成品ID
         * @return 成品视图对象，包含成品的详细信息
         * @throws BusinessException 如果成品不存在时抛出业务异常
         */
        @Override
        public FinishedProductVO getFinishedProductById(String id) {
            // 查找成品记录
            FinishedProduct product = finishedProductRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("成品不存在"));
            return convertToFinishedProductVO(product);
        }


        /**
         * 创建成品入库记录，初始化库存信息
         * <p>
         * 该方法用于创建新的成品记录并初始化库存，主要功能包括：
         * 1. 验证初始库存数量的合法性（不能为负数）
         * 2. 创建成品对象并设置基本信息（批次号、名称、产品编码、分类、颜色、尺寸等）
         * 3. 如果初始数量大于0且提供了位置信息，则创建位置库存记录
         * 4. 保存成品记录到数据库
         * </p>
         *
         * @param request 成品创建请求参数，包含批次号、名称、产品编码、分类、颜色、尺寸、单位、数量、预警阈值、价格、成本价、描述等信息
         * @return 创建后的成品视图对象
         * @throws BusinessException 如果库存数量小于0时抛出业务异常
         */
        @Override
        public FinishedProductVO createFinishedProduct(FinishedProductCreateRequest request) {
            // 获取并验证初始库存数量
            int initialQuantity = request.getQuantity() != null ? request.getQuantity() : 0;
            if (initialQuantity < 0) {
                throw new BusinessException("库存数量不能小于0");
            }

            // 创建成品对象并设置基本信息
            FinishedProduct product = new FinishedProduct();
            product.setBatchNo(request.getBatchNo());
            product.setName(request.getName());
            product.setProductCode(request.getProductCode());
            product.setCategory(request.getCategory());
            product.setColor(request.getColor());
            product.setSize(request.getSize());
            product.setUnit(request.getUnit());
            product.setQuantity(initialQuantity);
            product.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 0);
            product.setPrice(request.getPrice());
            product.setCostPrice(request.getCostPrice());
            product.setDescription(request.getDescription());

            // 如果初始数量大于0且提供了位置信息，则创建位置库存记录
            List<LocationInfo> locations = new ArrayList<>();
            if (initialQuantity > 0 && StringUtils.hasText(request.getLocation())) {
                LocationInfo info = new LocationInfo();
                info.setLocation(request.getLocation().trim());
                info.setQuantity(initialQuantity);
                info.setCreatedAt(new Date());
                locations.add(info);
            }
            product.setLocations(locations);

            // 保存成品记录
            FinishedProduct saved = finishedProductRepository.save(product);
            return convertToFinishedProductVO(saved);
        }


        /**
         * 更新成品信息，支持部分字段更新
         * <p>
         * 该方法用于更新现有成品的详细信息，主要功能包括：
         * 1. 查找并验证成品是否存在
         * 2. 根据请求参数选择性更新各个字段
         * 3. 保存更新后的成品记录
         * </p>
         *
         * @param id 成品ID
         * @param request 成品更新请求参数，包含需要更新的字段（支持部分更新）
         * @return 更新后的成品视图对象
         * @throws BusinessException 如果成品不存在时抛出业务异常
         */
        @Override
        public FinishedProductVO updateFinishedProduct(String id, FinishedProductUpdateRequest request) {
            // 查找成品记录
            FinishedProduct product = finishedProductRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("成品不存在"));

            // 选择性更新各个字段
            if (request.getName() != null) {
                product.setName(request.getName());
            }
            if (request.getCategory() != null) {
                product.setCategory(request.getCategory());
            }
            if (request.getColor() != null) {
                product.setColor(request.getColor());
            }
            if (request.getSize() != null) {
                product.setSize(request.getSize());
            }
            if (request.getUnit() != null) {
                product.setUnit(request.getUnit());
            }
            if (request.getPrice() != null) {
                product.setPrice(request.getPrice());
            }
            if (request.getCostPrice() != null) {
                product.setCostPrice(request.getCostPrice());
            }
            if (request.getDescription() != null) {
                product.setDescription(request.getDescription());
            }

            // 保存更新后的成品记录
            FinishedProduct saved = finishedProductRepository.save(product);
            return convertToFinishedProductVO(saved);
        }


        /**
         * 删除成品记录
         * <p>
         * 该方法根据ID从数据库中删除成品记录。
         * 删除前会先验证成品是否存在。
         * </p>
         *
         * @param id 成品ID
         * @throws BusinessException 如果成品不存在时抛出业务异常
         */
        @Override
        public void deleteFinishedProduct(String id) {
            // 验证成品是否存在
            if (!finishedProductRepository.existsById(id)) {
                throw new BusinessException("成品不存在");
            }
            // 执行删除操作
            finishedProductRepository.deleteById(id);
        }
    

        /**
         * 执行入库操作，支持原材料和成品入库
         * <p>
         * 该方法是库存管理的核心方法，支持两种物品类型的入库：
         * 1. 原材料入库：直接增加原材料库存数量，支持位置管理
         * 2. 成品入库：可以是从生产计划入库或现有成品直接入库
         *    - 如果是现有成品，直接增加库存
         *    - 如果是生产计划，验证计划状态必须为“COMPLETED”，然后创建或查找成品并入库
         * 3. 记录入库操作日志
         * </p>
         *
         * @param request 入库请求参数，包含物品类型（RAW_MATERIAL/FINISHED_PRODUCT）、物品ID、数量、原因、位置等信息
         * @param operatorId 操作员ID
         * @return 入库记录视图对象
         * @throws BusinessException 如果物品不存在、生产计划未完成或库存更新失败时抛出业务异常
         */
        @Override
        public InventoryRecordVO stockIn(StockInOutRequest request, String operatorId) {
            // 记录入库操作开始日志
            log.info("开始入库操作 - 类型: {}, 物品ID: {}, 数量: {}, 操作人: {}",
                    request.getItemType(), request.getItemId(), request.getQuantity(), operatorId);

            String itemName = "";
            String recordReason = request.getReason();
            // 处理原材料入库
            if ("RAW_MATERIAL".equals(request.getItemType())) {
                RawMaterial material = rawMaterialRepository.findById(request.getItemId())
                        .orElseThrow(() -> {
                            log.error("原材料不存在，ID: {}", request.getItemId());
                            return new BusinessException("原材料不存在，ID: " + request.getItemId());
                        });
                if (request.getReason() != null && request.getReason().contains("位置:")) {
                    String location = extractLocation(request.getReason());
                    if (location != null && !location.isEmpty()) {
                        addOrUpdateRawMaterialLocation(material, location, request.getQuantity());
                        log.info("更新原材料存放位置: {} 数量: {}", location, request.getQuantity());
                    }
                } else {
                    boolean increased = mongoAtomicOpsService.changeRawMaterialQuantity(request.getItemId(), request.getQuantity(), null);
                    if (!increased) {
                        throw new BusinessException("库存更新失败，请刷新后重试");
                        }
                    material.setQuantity((material.getQuantity() != null ? material.getQuantity() : 0) + request.getQuantity());
                }

                if (material.getLocations() != null && !material.getLocations().isEmpty()) {
                    recalculateRawMaterialQuantity(material);
                    saveRawMaterialWithLegacyVersionRetry(material);
                }
                itemName = material.getName();
                log.info("原材料入库成功 - 名称: {}, 新库存: {}", itemName, material.getQuantity());
            } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
                FinishedProduct existingProduct = finishedProductRepository.findById(request.getItemId()).orElse(null);
                if (existingProduct != null) {
                    int oldQuantity = existingProduct.getQuantity() != null ? existingProduct.getQuantity() : 0;
                    String location = extractLocation(request.getReason());
                    if ((location == null || location.isEmpty()) && request.getReason() != null && request.getReason().contains("位置:")) {
                        int startIndex = request.getReason().indexOf("位置:") + 3;
                        int endIndex = request.getReason().indexOf(" |", startIndex);
                        if (endIndex == -1) {
                            endIndex = request.getReason().length();
                        }
                        location = request.getReason().substring(startIndex, endIndex).trim();
                    }
                    if (location != null && !location.isEmpty()) {
                        addOrUpdateLocation(existingProduct, location, request.getQuantity());
                        log.info("更新成品位置: {} 数量: {}", location, request.getQuantity());
                    } else {
                        existingProduct.setQuantity(oldQuantity + request.getQuantity());
                    }

                    recalculateFinishedProductQuantity(existingProduct);
                    finishedProductRepository.save(existingProduct);
                    itemName = existingProduct.getName();

                    String operatorName = getOperatorName(operatorId);
                    InventoryRecord record = new InventoryRecord();
                    record.setInventoryType("IN");
                    record.setItemType(request.getItemType());
                    record.setItemId(existingProduct.getId());
                    record.setItemName(itemName);
                    record.setQuantity(request.getQuantity());
                    record.setOperator(operatorId);
                    record.setOperatorName(operatorName);
                    record.setReason(request.getReason());

                    InventoryRecord saved = inventoryRecordRepository.save(record);
                    log.info("现有成品入库成功 - id: {}, 数量: {}, 原有: {}, 现有: {}",
                            existingProduct.getId(), request.getQuantity(), oldQuantity, existingProduct.getQuantity());
                    return convertToInventoryRecordVO(saved);
                }
                log.info("成品入库 - 查找生产计划，ID: {}", request.getItemId());

                ProductionPlan plan = productionPlanRepository.findById(request.getItemId())
                        .orElseThrow(() -> {
                            log.error("生产计划不存在，ID: {}", request.getItemId());
                            return new BusinessException("生产计划不存在，ID: " + request.getItemId());
                        });

                log.info("找到生产计划 - 批次号: {}, 产品名: {}, 状态: {}",
                        plan.getBatchNo(), plan.getProductName(), plan.getStatus());

                if (!"COMPLETED".equals(plan.getStatus())) {
                    log.error("生产计划未完成，无法入库 - 计划ID: {}, 当前状态: {}",
                            plan.getId(), plan.getStatus());
                    throw new BusinessException("生产计划尚未完成，无法入库。当前状态：" + getPlanStatusText(plan.getStatus()));
                }

                FinishedProduct product = findOrCreateFinishedProduct(plan);

                int oldQuantity = 0;
                if (request.getReason() != null && request.getReason().contains("位置:")) {
                    String location = extractLocation(request.getReason());
                    if (location != null && !location.isEmpty()) {
                        addOrUpdateLocation(product, location, request.getQuantity());
                        log.info("更新成品存放位置: {} 数量: {}", location, request.getQuantity());
                    }
                } else {
                    oldQuantity = product.getQuantity();
                    product.setQuantity(oldQuantity + request.getQuantity());
                }

                recalculateFinishedProductQuantity(product);
                finishedProductRepository.save(product);
                itemName = product.getName();

                int oldStockedInQuantity = plan.getStockedInQuantity() != null ? plan.getStockedInQuantity() : 0;
                plan.setStockedInQuantity(oldStockedInQuantity + request.getQuantity());
                productionPlanRepository.save(plan);

                log.info("成品入库成功 - 名称: {}, 成品ID: {}, 入库数量: {}, 旧库存: {}, 新库存: {}, 计划已入库: {}/{}",
                        itemName, product.getId(), request.getQuantity(), oldQuantity, product.getQuantity(),
                        plan.getStockedInQuantity(), plan.getCompletedQuantity());
            } else {
                String itemTypeText = getItemTypeText(request.getItemType());
                log.error("无效的物品类型: {}", itemTypeText);
                throw new BusinessException("无效的物品类型：" + itemTypeText);
            }

            String operatorName = getOperatorName(operatorId);

            InventoryRecord record = new InventoryRecord();
            record.setInventoryType("IN");
            record.setItemType(request.getItemType());
            record.setItemId(request.getItemId());
            record.setItemName(itemName);
            record.setQuantity(request.getQuantity());
            record.setOperator(operatorId);
            record.setOperatorName(operatorName);
            record.setReason(recordReason);

            InventoryRecord saved = inventoryRecordRepository.save(record);
            log.info("入库记录已保存 - 记录ID: {}, 物品: {}, 数量: {}",
                    saved.getId(), itemName, request.getQuantity());

            return convertToInventoryRecordVO(saved);
        }


        /**
         * 执行出库操作，支持原材料和成品出库，优先从指定位置出库
         * <p>
         * 该方法统一处理原材料和成品出库，支持指定库位扣减、库位 FIFO 扣减、总库存扣减、库存预警检查和库存流水记录。
         * </p>
         *
         * @param request 出库请求参数，包含物品类型、物品ID、数量、原因等
         * @param operatorId 操作员ID
         * @return 出库记录视图对象
         * @throws BusinessException 如果物品不存在、库存不足、出库位置无效或物品类型无效时抛出业务异常
         */
        @Override
        public InventoryRecordVO stockOut(StockInOutRequest request, String operatorId) {
            // 记录出库入口日志，便于排查库存变动来源。
            log.info("开始出库操作 - 类型: {}, 物品ID: {}, 数量: {}, 操作人: {}",
                    request.getItemType(), request.getItemId(), request.getQuantity(), operatorId);

            // 初始化库存流水所需的物品名称和记录原因。
            String itemName = "";
            String recordReason = request.getReason();
            // 原材料出库分支。
            if ("RAW_MATERIAL".equals(request.getItemType())) {
                // 查询原材料主数据，确保出库目标存在。
                RawMaterial material = rawMaterialRepository.findById(request.getItemId())
                        .orElseThrow(() -> new BusinessException("原材料不存在"));

                // 从出库原因中解析指定库位。
                String location = extractLocation(request.getReason());
                // 判断当前原材料是否启用了库位库存。
                boolean hadLocationInventory = material.getLocations() != null && !material.getLocations().isEmpty();
                // 有库位库存时优先按指定库位或 FIFO 库位扣减。
                if (hadLocationInventory) {
                    // 指定库位时仅扣减目标库位。
                    if (StringUtils.hasText(location)) {
                        // 查找指定源库位。
                        LocationInfo targetLoc = material.getLocations().stream()
                                .filter(l -> location.equals(l.getLocation()))
                                .findFirst()
                                .orElse(null);
                        // 指定库位不存在时中断出库。
                        if (targetLoc == null) {
                            throw new BusinessException("出库位置不存在: " + location);
                        }
                        // 指定库位库存不足时中断出库。
                        if (targetLoc.getQuantity() < request.getQuantity()) {
                            throw new BusinessException(String.format(
                                "位置 %s 库存不足，当前可用: %d，请求出库: %d",
                                location, targetLoc.getQuantity(), request.getQuantity()));
                    }
                        // 记录指定库位扣减前的信息。
                        log.info("原材料出库 - 名称: {}, 位置: {}, 出库数量: {}, 位置原库存: {}",
                            material.getName(), location, request.getQuantity(), targetLoc.getQuantity());
                        // 扣减指定库位数量，并清理扣减后为 0 的库位。
                        targetLoc.setQuantity(targetLoc.getQuantity() - request.getQuantity());
                        material.getLocations().removeIf(l -> l.getQuantity() <= 0);
                    } else {
                        // 未指定库位时按 FIFO 规则从最早入库位置依次扣减。
                        String deductionLog = deductRawMaterialFromLocations(material, request.getQuantity());
                        // 将 FIFO 扣减明细追加到库存流水原因中。
                        recordReason = appendFifoDetail(request.getReason(), deductionLog);
                    }
                } else {
                    // 无库位库存时先校验总库存是否足够。
                    if (material.getQuantity() < request.getQuantity()) {
                        throw new BusinessException("库存不足，当前库存：" + material.getQuantity());
                        }
                    // 使用原子操作扣减总库存，避免并发出库导致库存穿透。
                    boolean deducted = mongoAtomicOpsService.changeRawMaterialQuantity(request.getItemId(), -request.getQuantity(), 0);
                    if (!deducted) {
                        throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
                        }
                    // 同步当前内存对象数量，供后续预警和返回信息使用。
                    material.setQuantity(material.getQuantity() - request.getQuantity());
                }

                // 有库位库存时重新汇总总库存并保存。
                if (hadLocationInventory) {
                    recalculateRawMaterialQuantity(material);
                    saveRawMaterialWithLegacyVersionRetry(material);
                }
                // 设置库存流水中的物品名称。
                itemName = material.getName();

                // 出库后库存低于阈值时创建库存预警。
                if (material.getAlertThreshold() != null && material.getQuantity() <= material.getAlertThreshold()) {
                    createAlertIfNeeded("RAW_MATERIAL", material.getId(), material.getName(),
                            material.getQuantity(), material.getAlertThreshold());
                }

                // 记录原材料出库完成日志。
                log.info("原材料出库成功 - 名称: {}, 原材料ID: {}, 出库数量: {}, 新库存: {}, 剩余位置数: {}",
                        itemName, material.getId(), request.getQuantity(), material.getQuantity(),
                        material.getLocations() != null ? material.getLocations().size() : 0);
            // 成品出库分支。
            } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
                // 查询成品主数据，确保出库目标存在。
                FinishedProduct product = finishedProductRepository.findById(request.getItemId())
                        .orElseThrow(() -> new BusinessException("成品不存在"));

                // 解析指定出库库位。
                String location = extractLocation(request.getReason());
                // 指定库位且成品存在库位库存时，从指定库位扣减。
                if (StringUtils.hasText(location) && product.getLocations() != null && !product.getLocations().isEmpty()) {
                    // 查找目标库位。
                    LocationInfo targetLoc = product.getLocations().stream()
                            .filter(l -> location.equals(l.getLocation()))
                            .findFirst()
                            .orElse(null);
                    // 指定库位不存在时中断出库。
                    if (targetLoc == null) {
                            throw new BusinessException("出库位置不存在: " + location);
                        }
                    // 指定库位库存不足时中断出库。
                    if (targetLoc.getQuantity() < request.getQuantity()) {
                            throw new BusinessException(String.format(
                                "位置 %s 库存不足，当前可用: %d，请求出库: %d",
                                location, targetLoc.getQuantity(), request.getQuantity()));
                    }
                    // 记录指定库位出库前的信息。
                    log.info("成品出库 - 名称: {}, 位置: {}, 出库数量: {}, 位置原库存: {}",
                            product.getName(), location, request.getQuantity(), targetLoc.getQuantity());
                    // 扣减指定库位数量，并清理空库位。
                    targetLoc.setQuantity(targetLoc.getQuantity() - request.getQuantity());
                    product.getLocations().removeIf(l -> l.getQuantity() <= 0);
                } else {
                    // 未指定库位或无库位库存时，按成品总库存扣减。
                    if (product.getQuantity() < request.getQuantity()) {
                        throw new BusinessException("库存不足，当前库存：" + product.getQuantity());
                        }
                    // 更新成品总库存数量。
                    product.setQuantity(product.getQuantity() - request.getQuantity());
                }

                // 重新计算成品总库存并保存出库结果。
                recalculateFinishedProductQuantity(product);
                finishedProductRepository.save(product);
                // 设置库存流水中的物品名称。
                itemName = product.getName();

                // 出库后库存低于阈值时创建成品预警。
                if (product.getAlertThreshold() != null && product.getQuantity() <= product.getAlertThreshold()) {
                    createAlertIfNeeded("FINISHED_PRODUCT", product.getId(), product.getName(),
                            product.getQuantity(), product.getAlertThreshold());
                }

                // 记录成品出库完成日志。
                log.info("成品出库成功 - 名称: {}, 成品ID: {}, 出库数量: {}, 新库存: {}, 剩余位置数: {}",
                        itemName, product.getId(), request.getQuantity(), product.getQuantity(),
                        product.getLocations() != null ? product.getLocations().size() : 0);
            } else {
                // 其他物品类型不受支持，直接抛出业务异常。
                throw new BusinessException("无效的物品类型");
            }

            // 解析操作人姓名，用于库存流水展示。
            String operatorName = getOperatorName(operatorId);

            // 创建出库流水记录。
            InventoryRecord record = new InventoryRecord();
            record.setInventoryType("OUT");
            record.setItemType(request.getItemType());
            record.setItemId(request.getItemId());
            record.setItemName(itemName);
            record.setQuantity(request.getQuantity());
            record.setOperator(operatorId);
            record.setOperatorName(operatorName);
            record.setReason(recordReason);

            // 保存出库流水并转换为视图对象返回。
            InventoryRecord saved = inventoryRecordRepository.save(record);
            return convertToInventoryRecordVO(saved);
        }


        /**
         * 查询库存记录列表，支持物品类型和ID筛选及分页，按创建时间降序排序
         * <p>
         * 该方法读取全部库存流水后在内存中按物品类型、物品 ID 过滤，再按创建时间倒序分页返回。
         * </p>
         *
         * @param itemType 物品类型（RAW_MATERIAL-原材料，FINISHED_PRODUCT-成品）
         * @param itemId 物品ID
         * @param pageable 分页参数
         * @return 分页的库存记录视图对象列表
         */
        @Override
        public Page<InventoryRecordVO> getInventoryRecords(String itemType, String itemId, Pageable pageable) {
            // 查询全部库存流水记录。
            List<InventoryRecord> all = inventoryRecordRepository.findAll();
            // 按物品类型和物品 ID 进行可选过滤。
            List<InventoryRecord> filtered = all.stream()
                    .filter(r -> !StringUtils.hasText(itemType) || itemType.equals(r.getItemType()))
                    .filter(r -> !StringUtils.hasText(itemId) || itemId.equals(r.getItemId()))
                    .collect(Collectors.toList());

            // 按创建时间倒序排列，空时间排在末尾。
            filtered.sort((a, b) -> {
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });

            // 根据 Pageable 计算当前页数据范围。
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<InventoryRecord> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

            // 将当前页库存流水实体转换为视图对象。
            List<InventoryRecordVO> voList = pageContent.stream()
                    .map(this::convertToInventoryRecordVO)
                    .collect(Collectors.toList());

            // 返回 Spring 分页结果。
            return new PageImpl<>(voList, pageable, filtered.size());
        }


        /**
         * 查询库存预警列表，支持状态筛选及分页，按创建时间降序排序
         * <p>
         * 该方法读取库存预警记录，按状态进行可选过滤，并按创建时间倒序分页返回。
         * </p>
         *
         * @param status 预警状态（active-活跃，resolved-已解决）
         * @param pageable 分页参数
         * @return 分页的库存预警视图对象列表
         */
        @Override
        public Page<InventoryAlertVO> getAlerts(String status, Pageable pageable) {
            // 查询全部库存预警记录。
            List<InventoryAlert> all = inventoryAlertRepository.findAll();
            // 按状态进行可选过滤。
            List<InventoryAlert> filtered = all.stream()
                    .filter(a -> !StringUtils.hasText(status) || status.equals(a.getStatus()))
                    .collect(Collectors.toList());

            // 按创建时间倒序排序，空时间排在末尾。
            filtered.sort((a, b) -> {
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });

            // 根据分页参数截取当前页数据。
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<InventoryAlert> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

            // 将预警实体转换为接口视图对象。
            List<InventoryAlertVO> voList = pageContent.stream()
                    .map(this::convertToInventoryAlertVO)
                    .collect(Collectors.toList());

            // 返回分页预警结果。
            return new PageImpl<>(voList, pageable, filtered.size());
        }


        /**
         * 处理库存预警，将预警状态更新为已处理
         * <p>
         * 该方法只允许处理待处理预警，并通过原子操作更新预警状态，避免同一条预警被并发重复处理。
         * </p>
         *
         * @param id 预警ID
         * @param request 预警处理请求参数，包含处理人信息
         * @return 处理后的库存预警视图对象
         * @throws BusinessException 如果预警不存在、已处理或状态被并发修改时抛出业务异常
         */
        @Override
        public InventoryAlertVO handleAlert(String id, AlertHandleRequest request) {
            // 查询预警记录，确保处理目标存在。
            InventoryAlert alert = inventoryAlertRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("预警记录不存在"));

            // 仅允许处理仍处于待处理状态的预警。
            if (!"PENDING".equals(alert.getStatus())) {
                throw new BusinessException("该预警已处理");
            }

            // 记录处理时间，用于落库和返回展示。
            Date handleTime = new Date();
            // 使用 Mongo 原子操作处理预警，防止并发重复处理。
            boolean handled = mongoAtomicOpsService.handleInventoryAlert(id, request.getHandleBy(), handleTime);
            // 原子处理失败表示预警状态已经变化。
            if (!handled) {
                throw new BusinessException("预警状态已变更，请刷新后重试");
            }

            // 同步当前内存对象字段，避免再次查询即可返回最新视图。
            alert.setStatus("HANDLED");
            alert.setHandleTime(handleTime);
            alert.setHandleBy(request.getHandleBy());
            alert.setOpenAlertKey(null);

            // 转换为预警视图对象返回。
            return convertToInventoryAlertVO(alert);
        }


        /**
         * 设置原材料库存预警阈值
         * <p>
         * 该方法根据原材料 ID 更新预警阈值，并返回更新后的原材料视图。
         * </p>
         *
         * @param id 原材料ID
         * @param request 阈值设置请求参数，包含预警阈值
         * @return 更新后的原材料视图对象
         * @throws BusinessException 如果原材料不存在时抛出业务异常
         */
        @Override
        public RawMaterialVO setRawMaterialThreshold(String id, ThresholdRequest request) {
            // 查询原材料，确保阈值设置目标存在。
            RawMaterial material = rawMaterialRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 更新原材料预警阈值。
            material.setAlertThreshold(request.getAlertThreshold());
            // 保存阈值并返回最新视图。
            RawMaterial saved = rawMaterialRepository.save(material);
            return convertToRawMaterialVO(saved);
        }
    

        /**
         * 设置成品库存预警阈值
         * <p>
         * 该方法根据成品 ID 更新预警阈值，并返回更新后的成品视图。
         * </p>
         *
         * @param id 成品ID
         * @param request 阈值设置请求参数，包含预警阈值
         * @return 更新后的成品视图对象
         * @throws BusinessException 如果成品不存在时抛出业务异常
         */
        @Override
        public FinishedProductVO setFinishedProductThreshold(String id, ThresholdRequest request) {
            // 查询成品，确保阈值设置目标存在。
            FinishedProduct product = finishedProductRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("成品不存在"));

            // 更新成品预警阈值。
            product.setAlertThreshold(request.getAlertThreshold());
            // 保存阈值并返回最新视图。
            FinishedProduct saved = finishedProductRepository.save(product);
            return convertToFinishedProductVO(saved);
        }


        /**
         * 在库存低于阈值时创建预警记录，如果已存在活跃预警则忽略
         * <p>
         * 该方法创建待处理预警，并通过唯一键避免同一物品产生重复打开状态的预警。
         * </p>
         *
         * @param itemType 物品类型（RAW_MATERIAL或FINISHED_PRODUCT）
         * @param itemId 物品ID
         * @param itemName 物品名称
         * @param currentQuantity 当前库存数量
         * @param threshold 预警阈值
         */
        private void createAlertIfNeeded(String itemType, String itemId, String itemName,
                                          Integer currentQuantity, Integer threshold) {
            // 构建新的待处理库存预警对象。
            InventoryAlert alert = new InventoryAlert();
            alert.setItemType(itemType);
            alert.setItemId(itemId);
            alert.setItemName(itemName);
            alert.setCurrentQuantity(currentQuantity);
            alert.setThreshold(threshold);
            alert.setStatus("PENDING");
            // 打开预警唯一键用于防止同一物品重复创建待处理预警。
            alert.setOpenAlertKey(itemType + ":" + itemId);
            // 保存预警；如果唯一键冲突，说明已有打开预警，直接忽略。
            try {
                inventoryAlertRepository.save(alert);
            } catch (DuplicateKeyException ex) {
                log.debug("预警已存在，忽略重复创建 - itemType: {}, itemId: {}", itemType, itemId);
            }
        }


        /**
         * 根据用户ID获取操作员真实姓名
         * <p>
         * 该方法用于库存流水展示操作人姓名，用户不存在时返回空字符串兜底。
         * </p>
         *
         * @param operatorId 操作员ID
         * @return 操作员真实姓名，如果不存在则返回空字符串
         */
        private String getOperatorName(String operatorId) {
            // 根据用户 ID 查询真实姓名，查询不到时返回空字符串。
            return userRepository.findById(operatorId)
                    .map(User::getRealName)
                    .orElse("");
        }


        /**
         * 查找或创建成品记录，如果已存在则返回现有成品，否则根据生产计划创建新成品
         * <p>
         * 该方法用于生产计划成品入库场景，先匹配已有成品，找不到时再按生产计划创建新成品。
         * </p>
         *
         * @param plan 生产计划对象
         * @return 成品对象（现有或新建）
         */
        private FinishedProduct findOrCreateFinishedProduct(ProductionPlan plan) {
            // 优先查找与生产计划完全匹配的已有成品。
            FinishedProduct existing = findMatchingFinishedProduct(plan);
            // 如果已存在匹配成品，直接复用。
            if (existing != null) {
                return existing;
            }

            // 未找到匹配成品时，根据生产计划创建成品基础信息。
            FinishedProduct newProduct = new FinishedProduct();
            newProduct.setProductCode(plan.getProductCode());
            newProduct.setName(plan.getProductName());
            newProduct.setCategory(plan.getCategory());
            newProduct.setColor(plan.getColor());
            newProduct.setSize(plan.getSize());
            newProduct.setBatchNo(plan.getBatchNo());
            newProduct.setQuantity(0);
            newProduct.setUnit(plan.getUnit());
            newProduct.setDescription("自动创建 - 来源: " + plan.getBatchNo());

            // 保存新成品；遇到并发重复创建时重新查询并复用并发创建的成品。
            try {
                return finishedProductRepository.save(newProduct);
            } catch (DuplicateKeyException ex) {
                // 唯一键冲突后再次查找匹配成品，兼容并发入库场景。
                FinishedProduct concurrentProduct = findMatchingFinishedProduct(plan);
                if (concurrentProduct != null) {
                    return concurrentProduct;
                }
                // 如果仍找不到匹配成品，则保留原异常向上抛出。
                throw ex;
            }
        }


        /**
         * 查找与生产计划匹配的成品，优先使用精确查询，其次使用模糊匹配
         * <p>
         * 该方法先通过仓库精确匹配产品编码、名称、颜色、尺寸和批次号，失败后遍历成品做标准化文本匹配。
         * </p>
         *
         * @param plan 生产计划对象
         * @return 匹配的成品对象，如果不存在则返回null
         */
        private FinishedProduct findMatchingFinishedProduct(ProductionPlan plan) {
            // 先使用仓库方法按关键字段精确匹配。
            return finishedProductRepository.findFirstByProductCodeAndNameAndColorAndSizeAndBatchNo(
                            plan.getProductCode(),
                            plan.getProductName(),
                            plan.getColor(),
                            plan.getSize(),
                            plan.getBatchNo())
                    // 精确匹配失败时，遍历全部成品并使用标准化文本做兜底匹配。
                    .orElseGet(() -> finishedProductRepository.findAll().stream()
                            .filter(product -> matchesFinishedProductIdentity(product, plan))
                            .findFirst()
                            .orElse(null));
        }


        /**
         * 判断成品与生产计划是否匹配，通过批次号、名称、产品编码、颜色和尺寸进行比对
         * <p>
         * 该方法对生产计划和成品的关键身份字段做标准化比较，避免首尾空格和空值影响匹配结果。
         * </p>
         *
         * @param product 成品对象
         * @param plan 生产计划对象
         * @return 如果所有关键字段匹配则返回true
         */
        private boolean matchesFinishedProductIdentity(FinishedProduct product, ProductionPlan plan) {
            // 所有关键字段都一致时，才认为该成品与生产计划匹配。
            return sameText(product.getBatchNo(), plan.getBatchNo())
                    && sameText(product.getName(), plan.getProductName())
                    && sameText(product.getProductCode(), plan.getProductCode())
                    && sameText(product.getColor(), plan.getColor())
                    && sameText(product.getSize(), plan.getSize());
        }


        /**
         * 比较两个文本是否相同（忽略大小写和首尾空格）
         * <p>
         * 该方法通过统一标准化空值和首尾空格，降低库存匹配时的文本差异影响。
         * </p>
         *
         * @param left 第一个文本
         * @param right 第二个文本
         * @return 如果标准化后相等则返回true
         */
        private boolean sameText(String left, String right) {
            // 两侧文本标准化后再比较，空值会统一转换为空字符串。
            return normalizeText(left).equals(normalizeText(right));
        }


        /**
         * 标准化文本，去除首尾空格，空值转换为空字符串
         * <p>
         * 该方法用于成品匹配和库存身份字段比较，避免 null 与空白字符串导致误判。
         * </p>
         *
         * @param value 待标准化的文本
         * @return 标准化后的文本
         */
        private String normalizeText(String value) {
            // 有实际内容时去除首尾空格，否则统一返回空字符串。
            return StringUtils.hasText(value) ? value.trim() : "";
        }


        /**
         * 从原因字符串中提取位置信息，格式为"位置:xxx"
         * <p>
         * 该方法解析库存出入库原因中的库位片段，供指定库位入库、出库和移动等逻辑复用。
         * </p>
         *
         * @param reason 原因字符串
         * @return 提取的位置信息，如果不存在则返回null
         */
        private String extractLocation(String reason) {
            // 原因为空或不包含位置标记时，不解析库位。
            if (reason == null || !reason.contains("位置:")) {
                return null;
            }
            // 定位“位置:”后面的起始位置。
            int startIndex = reason.indexOf("位置:") + 3;
            // 如果后面还有“ |”分隔内容，则以分隔符作为库位结束位置。
            int endIndex = reason.indexOf(" |", startIndex);
            // 没有分隔符时，取到字符串末尾。
            if (endIndex == -1) {
                endIndex = reason.length();
            }
            // 返回去除首尾空格后的库位文本。
            return reason.substring(startIndex, endIndex).trim();
        }

    

    

        /**
         * 按先进先出（FIFO）原则从多个位置扣减原材料库存
         * <p>
         * 该方法按照库位创建时间从早到晚扣减原材料库存，并返回每个库位的扣减明细。
         * </p>
         *
         * @param material 原材料对象
         * @param quantity 需要扣减的数量
         * @return 扣减明细日志字符串，格式为"位置1(数量1)位置2(数量2)"
         * @throws BusinessException 如果所有库位库存合计仍不足以扣减指定数量时抛出业务异常
         */
        private String deductRawMaterialFromLocations(RawMaterial material, int quantity) {
            // 复制库位列表用于排序，避免直接改变原列表顺序。
            List<LocationInfo> sortedLocations = new ArrayList<>(material.getLocations());
            // 按库位创建时间升序排列，空创建时间按最早时间处理。
            sortedLocations.sort((a, b) -> {
                Date aTime = a.getCreatedAt() != null ? a.getCreatedAt() : new Date(0);
                Date bTime = b.getCreatedAt() != null ? b.getCreatedAt() : new Date(0);
                return aTime.compareTo(bTime);
            });

            // 记录剩余待扣减数量。
            int remaining = quantity;
            // 收集每个库位实际扣减数量，用于库存流水原因展示。
            StringBuilder deductionLog = new StringBuilder();

            // 按 FIFO 顺序逐个库位扣减。
            for (LocationInfo loc : sortedLocations) {
                // 需求已扣满时提前退出。
                if (remaining <= 0) {
                    break;
                }

                // 空数量按 0 处理。
                int available = loc.getQuantity() != null ? loc.getQuantity() : 0;
                // 当前库位无库存时跳过。
                if (available <= 0) {
                    continue;
                }

                // 本次扣减数量取可用库存和剩余需求中的较小值。
                int deduct = Math.min(available, remaining);
                // 更新库位剩余数量。
                loc.setQuantity(available - deduct);
                // 更新剩余待扣减数量。
                remaining -= deduct;

                // 记录本次库位扣减日志和流水明细。
                log.info("FIFO扣减 - 位置: {}, 原库存: {}, 扣减: {}, 剩余: {}",
                        loc.getLocation(), available, deduct, loc.getQuantity());
                deductionLog.append(loc.getLocation()).append("(").append(deduct).append(")");
            }

            // 遍历完所有库位仍未扣满，说明库存不足。
            if (remaining > 0) {
                throw new BusinessException(String.format(
                        "FIFO扣减失败：位置总库存不足，需扣减 %d，实际可扣减 %d",
                        quantity, quantity - remaining));
            }

            // 清理扣减后数量为 0 的库位。
            material.getLocations().removeIf(l -> l.getQuantity() <= 0);
            // 返回扣减明细字符串。
            return deductionLog.toString();
        }

    

    

        /**
         * 将FIFO扣减明细追加到原因字符串中
         * <p>
         * 该方法统一生成库存流水中的 FIFO 详情文本，便于追踪扣减来源库位。
         * </p>
         *
         * @param reason 原始原因
         * @param deductionLog FIFO扣减明细日志
         * @return 追加后的原因字符串
         */
        private String appendFifoDetail(String reason, String deductionLog) {
            // 没有扣减明细时保持原原因不变。
            if (!StringUtils.hasText(deductionLog)) {
                return reason;
            }
            // 原因为空时使用默认文案作为前缀。
            String baseReason = StringUtils.hasText(reason) ? reason : "FIFO扣减";
            // 拼接 FIFO 明细。
            return baseReason + " [FIFO:" + deductionLog + "]";
        }

    

    

        /**
         * 保存原材料并处理版本冲突，如果因版本问题失败则尝试初始化版本号后重试
         * <p>
         * 该方法兼容历史数据缺少 version 字段的情况，保存失败后尝试初始化版本号并重新保存一次。
         * </p>
         *
         * @param material 原材料对象
         * @return 保存后的原材料对象
         */
        private RawMaterial saveRawMaterialWithLegacyVersionRetry(RawMaterial material) {
            // 首先按正常流程保存原材料。
            try {
                return rawMaterialRepository.save(material);
            } catch (DuplicateKeyException ex) {
                // 如果没有 ID 或无法初始化历史版本号，则保留原异常。
                if (!StringUtils.hasText(material.getId())
                        || !mongoAtomicOpsService.initializeRawMaterialVersionIfMissing(material.getId())) {
                    throw ex;
                }
                // 历史版本号初始化成功后重试保存。
                return rawMaterialRepository.save(material);
            }
        }

    

    

        /**
         * 重新计算原材料总库存数量，基于所有位置库存的总和
         * <p>
         * 该方法以库位库存汇总结果覆盖原材料总库存，保证总量和库位明细一致。
         * </p>
         *
         * @param material 原材料对象
         */
        private void recalculateRawMaterialQuantity(RawMaterial material) {
            // 默认总库存为 0。
            int total = 0;
            // 有库位列表时，汇总所有非空库位数量。
            if (material.getLocations() != null) {
                total = material.getLocations().stream()
                        .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                        .sum();
            }
            // 将汇总结果写回原材料总库存。
            material.setQuantity(total);
            // 输出调试日志，便于检查库位汇总情况。
            log.debug("重新计算原材料总库存 - ID: {}, 新总量: {}, 位置数: {}",
                    material.getId(), total, material.getLocations() != null ? material.getLocations().size() : 0);
        }

    

    

        /**
         * 重新计算并修正原材料库存数量，确保与位置库存总和一致
         * <p>
         * 该方法用于视图转换前的自修复，如果总库存和库位汇总不一致，会写回修正后的数量。
         * </p>
         *
         * @param material 原材料对象
         * @param repo 原材料仓库接口
         */
        private void recalculateAndFixQuantity(RawMaterial material, RawMaterialRepository repo) {
            // 默认正确总量为 0。
            int correctTotal = 0;
            // 有库位库存时，以库位数量汇总作为正确总量。
            if (material.getLocations() != null) {
                correctTotal = material.getLocations().stream()
                        .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                        .sum();
            }
            // 总库存为空或与库位汇总不一致时进行修正。
            if (material.getQuantity() == null || material.getQuantity() != correctTotal) {
                log.warn("检测到原材料数量不一致 - ID: {}, 原始quantity: {}, 正确总量: {}, 自动修正",
                        material.getId(), material.getQuantity(), correctTotal);
                material.setQuantity(correctTotal);
                // 保存修正后的原材料库存总量。
                repo.save(material);
            }
        }

    

    

        /**
         * 重新计算并修正成品库存数量，确保与位置库存总和一致
         * <p>
         * 该方法用于视图转换前的自修复，如果成品总库存和库位汇总不一致，会写回修正后的数量。
         * </p>
         *
         * @param product 成品对象
         * @param repo 成品仓库接口
         */
        private void recalculateAndFixFinishedProductQuantity(FinishedProduct product, FinishedProductRepository repo) {
            // 默认正确总量为 0。
            int correctTotal = 0;
            // 有库位库存时，以库位数量汇总作为正确总量。
            if (product.getLocations() != null) {
                correctTotal = product.getLocations().stream()
                        .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                        .sum();
            }
            // 总库存为空或与库位汇总不一致时进行修正。
            if (product.getQuantity() == null || product.getQuantity() != correctTotal) {
                log.warn("检测到成品数量不一致 - ID: {}, 原始quantity: {}, 正确总量: {}, 自动修正",
                        product.getId(), product.getQuantity(), correctTotal);
                product.setQuantity(correctTotal);
                // 保存修正后的成品库存总量。
                repo.save(product);
            }
        }

    

    

        /**
         * 重新计算成品总库存数量，基于所有位置库存的总和
         * <p>
         * 该方法以库位库存汇总结果覆盖成品总库存，保证总量和库位明细一致。
         * </p>
         *
         * @param product 成品对象
         */
        private void recalculateFinishedProductQuantity(FinishedProduct product) {
            // 默认总库存为 0。
            int total = 0;
            // 有库位列表时，汇总所有非空库位数量。
            if (product.getLocations() != null) {
                total = product.getLocations().stream()
                        .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                        .sum();
            }
            // 将汇总结果写回成品总库存。
            product.setQuantity(total);
            // 输出调试日志，便于检查库位汇总情况。
            log.debug("重新计算成品总库存 - ID: {}, 新总量: {}, 位置数: {}",
                    product.getId(), total, product.getLocations() != null ? product.getLocations().size() : 0);
        }

    

    

        /**
         * 添加或更新成品存放位置及库存数量
         * <p>
         * 该方法在成品库位存在时累加数量，不存在时创建新的库位记录。
         * </p>
         *
         * @param product 成品对象
         * @param location 存放位置
         * @param quantity 库存数量
         */
        private void addOrUpdateLocation(FinishedProduct product, String location, int quantity) {
            // 库位列表为空时先初始化。
            if (product.getLocations() == null) {
                product.setLocations(new ArrayList<>());
            }
            // 查找是否已经存在同名库位。
            LocationInfo existing = product.getLocations().stream()
                    .filter(l -> location.equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 已有库位时累加数量。
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
            } else {
                // 不存在库位时创建新库位并记录创建时间。
                LocationInfo newLocation = new LocationInfo();
                newLocation.setLocation(location);
                newLocation.setQuantity(quantity);
                newLocation.setCreatedAt(new Date());
                product.getLocations().add(newLocation);
            }
        }

    

    

        /**
         * 添加或更新原材料存放位置及库存数量
         * <p>
         * 该方法在原材料库位存在时累加数量，不存在时创建新的库位记录。
         * </p>
         *
         * @param material 原材料对象
         * @param location 存放位置
         * @param quantity 库存数量
         */
        private void addOrUpdateRawMaterialLocation(RawMaterial material, String location, int quantity) {
            // 库位列表为空时先初始化。
            if (material.getLocations() == null) {
                material.setLocations(new ArrayList<>());
            }
            // 查找是否已经存在同名库位。
            LocationInfo existing = material.getLocations().stream()
                    .filter(l -> location.equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 已有库位时累加数量。
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + quantity);
            } else {
                // 不存在库位时创建新库位并记录创建时间。
                LocationInfo newLocation = new LocationInfo();
                newLocation.setLocation(location);
                newLocation.setQuantity(quantity);
                newLocation.setCreatedAt(new Date());
                material.getLocations().add(newLocation);
            }
        }

    

    

        /**
         * 将原材料实体转换为视图对象，并在转换前修正库存数量
         * <p>
         * 该方法在返回原材料视图前校验并修正总库存，避免接口展示与库位明细不一致。
         * </p>
         *
         * @param material 原材料实体对象
         * @return 原材料视图对象
         */
        private RawMaterialVO convertToRawMaterialVO(RawMaterial material) {
            // 转换前先修正总库存和库位汇总不一致的问题。
            recalculateAndFixQuantity(material, rawMaterialRepository);
            // 使用构建器组装原材料视图字段。
            return RawMaterialVO.builder()
                    .id(material.getId())
                    .name(material.getName())
                    .category(material.getCategory())
                    .specification(material.getSpecification())
                    .unit(material.getUnit())
                    .quantity(material.getQuantity())
                    .alertThreshold(material.getAlertThreshold())
                    .locations(material.getLocations())
                    .supplier(material.getSupplier())
                    .price(material.getPrice())
                    .description(material.getDescription())
                    .createTime(material.getCreateTime())
                    .updateTime(material.getUpdateTime())
                    .build();
        }

    

    

        /**
         * 将成品实体转换为视图对象，并在转换前修正库存数量和解析成本价格
         * <p>
         * 该方法在返回成品视图前校验库存总量，并根据产品定义动态解析成本价。
         * </p>
         *
         * @param product 成品实体对象
         * @return 成品视图对象
         */
        private FinishedProductVO convertToFinishedProductVO(FinishedProduct product) {
            // 转换前先修正总库存和库位汇总不一致的问题。
            recalculateAndFixFinishedProductQuantity(product, finishedProductRepository);
            // 根据产品定义动态计算成品成本价。
            Double dynamicCostPrice = resolveFinishedProductCostPrice(product.getProductCode());
            // 使用构建器组装成品视图字段。
            return FinishedProductVO.builder()
                    .id(product.getId())
                    .productCode(product.getProductCode())
                    .name(product.getName())
                    .category(product.getCategory())
                    .color(product.getColor())
                    .size(product.getSize())
                    .batchNo(product.getBatchNo())
                    .unit(product.getUnit())
                    .quantity(product.getQuantity())
                    .alertThreshold(product.getAlertThreshold())
                    .locations(product.getLocations())
                    .price(product.getPrice())
                    .costPrice(dynamicCostPrice)
                    .description(product.getDescription())
                    .createTime(product.getCreateTime())
                    .updateTime(product.getUpdateTime())
                    .build();
        }

    

    

        /**
         * 根据产品定义中的原材料信息动态计算成品的成本价格
         * <p>
         * 该方法根据产品编码找到产品定义，遍历物料清单并按原材料价格与用量计算单件成本。
         * </p>
         *
         * @param productCode 产品编码
         * @return 计算得出的成本价格，如果无法计算则返回null
         */
        private Double resolveFinishedProductCostPrice(String productCode) {
            // 产品编码为空时无法匹配产品定义。
            if (!StringUtils.hasText(productCode)) {
                return null;
            }

            // 根据产品编码查询产品定义。
            ProductDefinition definition = productDefinitionRepository.findByProductCode(productCode).orElse(null);
            // 没有定义或物料清单为空时无法计算成本。
            if (definition == null || definition.getMaterials() == null || definition.getMaterials().isEmpty()) {
                return null;
            }

            // 累计单件成本。
            double unitCost = 0D;
            // 标记是否至少成功匹配到一个有效原材料。
            boolean hasMatchedMaterial = false;
            // 遍历产品定义中的原材料用量。
            for (ProductDefinition.ProductMaterial material : definition.getMaterials()) {
                // 物料 ID 为空时跳过。
                if (!StringUtils.hasText(material.getMaterialId())) {
                    continue;
                }

                // 查询原材料价格。
                RawMaterial rawMaterial = rawMaterialRepository.findById(material.getMaterialId()).orElse(null);
                // 原材料不存在、无价格或无用量时跳过。
                if (rawMaterial == null || rawMaterial.getPrice() == null || material.getQuantity() == null) {
                    continue;
                }

                // 按单价乘以用量累加成本。
                unitCost += rawMaterial.getPrice() * material.getQuantity();
                // 记录已经找到至少一个有效物料。
                hasMatchedMaterial = true;
            }

            // 至少匹配到一个有效物料才返回成本，否则返回 null。
            return hasMatchedMaterial ? unitCost : null;
        }

    

    

        /**
         * 将库存记录实体转换为视图对象
         * <p>
         * 该方法将库存流水实体中的展示字段复制到接口返回对象中。
         * </p>
         *
         * @param record 库存记录实体对象
         * @return 库存记录视图对象
         */
        private InventoryRecordVO convertToInventoryRecordVO(InventoryRecord record) {
            // 使用构建器组装库存流水视图字段。
            return InventoryRecordVO.builder()
                    .id(record.getId())
                    .inventoryType(record.getInventoryType())
                    .itemType(record.getItemType())
                    .itemId(record.getItemId())
                    .itemName(record.getItemName())
                    .quantity(record.getQuantity())
                    .operator(record.getOperator())
                    .operatorName(record.getOperatorName())
                    .reason(record.getReason())
                    .createTime(record.getCreateTime())
                    .build();
        }

    

    

        /**
         * 将库存预警实体转换为视图对象
         * <p>
         * 该方法将库存预警实体中的物品信息、阈值、状态和处理信息复制到接口返回对象中。
         * </p>
         *
         * @param alert 库存预警实体对象
         * @return 库存预警视图对象
         */
        private InventoryAlertVO convertToInventoryAlertVO(InventoryAlert alert) {
            // 使用构建器组装库存预警视图字段。
            return InventoryAlertVO.builder()
                    .id(alert.getId())
                    .itemType(alert.getItemType())
                    .itemId(alert.getItemId())
                    .itemName(alert.getItemName())
                    .currentQuantity(alert.getCurrentQuantity())
                    .threshold(alert.getThreshold())
                    .status(alert.getStatus())
                    .createTime(alert.getCreateTime())
                    .handleTime(alert.getHandleTime())
                    .handleBy(alert.getHandleBy())
                    .build();
        }

    

    

        /**
         * 移动原材料存放位置，支持跨库移动，自动更新源位置和目标位置的库存
         * <p>
         * 该方法校验源库位、目标库位和移动数量后，从源库位扣减并向目标库位累加，最后重新汇总原材料总库存。
         * </p>
         *
         * @param id 原材料ID
         * @param request 位置移动请求参数，包含源位置、目标位置和移动数量
         * @return 移动后的原材料视图对象
         * @throws BusinessException 如果位置参数无效、原材料不存在、源位置不存在或源位置库存不足时抛出业务异常
         */
        @Override
        public RawMaterialVO moveRawMaterialLocation(String id, MoveLocationRequest request) {
            // 记录跨库移动入口日志。
            log.info("开始跨库移动 - 原材料ID: {}, 源位置: {}, 目标位置: {}, 移动数量: {}",
                    id, request.getSourceLocation(), request.getTargetLocation(), request.getQuantity());

            // 源位置和目标位置都不能为空。
            if (!StringUtils.hasText(request.getSourceLocation()) || !StringUtils.hasText(request.getTargetLocation())) {
                throw new BusinessException("源位置和目标位置不能为空");
            }
            // 源位置和目标位置不能相同，否则移动没有意义。
            if (request.getSourceLocation().equals(request.getTargetLocation())) {
                throw new BusinessException("源位置和目标位置不能相同");
            }
            // 移动数量必须为正数。
            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                throw new BusinessException("移动数量必须大于0");
            }

            // 查询原材料，确保移动目标存在。
            RawMaterial material = rawMaterialRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 没有库位明细时无法执行跨库移动。
            if (material.getLocations() == null || material.getLocations().isEmpty()) {
                throw new BusinessException("该原材料暂无存放位置记录，无法执行跨库移动");
            }

            // 查找源库位。
            LocationInfo sourceLoc = material.getLocations().stream()
                    .filter(l -> request.getSourceLocation().equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 源库位不存在时中断移动。
            if (sourceLoc == null) {
                throw new BusinessException("源位置不存在: " + request.getSourceLocation());
            }
            // 源库位库存不足时中断移动。
            if (sourceLoc.getQuantity() < request.getQuantity()) {
                throw new BusinessException(String.format(
                        "源位置 %s 库存不足，当前可用: %d，请求移动: %d",
                        request.getSourceLocation(), sourceLoc.getQuantity(), request.getQuantity()));
            }

            // 从源库位扣减移动数量。
            sourceLoc.setQuantity(sourceLoc.getQuantity() - request.getQuantity());
            log.info("源位置 {} 减少后剩余: {}", sourceLoc.getLocation(), sourceLoc.getQuantity());

            // 查找目标库位。
            LocationInfo targetLoc = material.getLocations().stream()
                    .filter(l -> request.getTargetLocation().equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 目标库位已存在时直接累加数量。
            if (targetLoc != null) {
                targetLoc.setQuantity(targetLoc.getQuantity() + request.getQuantity());
                log.info("目标位置 {} 已存在，增加后: {}", targetLoc.getLocation(), targetLoc.getQuantity());
            } else {
                // 目标库位不存在时创建新库位。
                LocationInfo newTarget = new LocationInfo();
                newTarget.setLocation(request.getTargetLocation());
                newTarget.setQuantity(request.getQuantity());
                newTarget.setCreatedAt(new Date());
                material.getLocations().add(newTarget);
                log.info("创建新目标位置 {}: {}", request.getTargetLocation(), request.getQuantity());
            }

            // 清理移动后数量为 0 的源库位。
            material.getLocations().removeIf(l -> l.getQuantity() <= 0);

            // 重新汇总原材料总库存。
            recalculateRawMaterialQuantity(material);

            // 保存移动后的原材料数据。
            RawMaterial saved = rawMaterialRepository.save(material);

            // 记录跨库移动完成日志。
            log.info("跨库移动完成 - 原材料ID: {}, 新总量: {}, 剩余位置数: {}",
                    saved.getId(), saved.getQuantity(),
                    saved.getLocations() != null ? saved.getLocations().size() : 0);

            // 返回移动后的原材料视图。
            return convertToRawMaterialVO(saved);
        }

    

    

        /**
         * 移动成品存放位置，支持跨库移动，自动更新源位置和目标位置的库存
         * <p>
         * 该方法校验源库位、目标库位和移动数量后，从源库位扣减并向目标库位累加，最后重新汇总成品总库存。
         * </p>
         *
         * @param id 成品ID
         * @param request 位置移动请求参数，包含源位置、目标位置和移动数量
         * @return 移动后的成品视图对象
         * @throws BusinessException 如果位置参数无效、成品不存在、源位置不存在或源位置库存不足时抛出业务异常
         */
        @Override
        public FinishedProductVO moveFinishedProductLocation(String id, MoveLocationRequest request) {
            // 记录跨库移动入口日志。
            log.info("开始跨库移动 - 成品ID: {}, 源位置: {}, 目标位置: {}, 移动数量: {}",
                    id, request.getSourceLocation(), request.getTargetLocation(), request.getQuantity());

            // 源位置和目标位置都不能为空。
            if (!StringUtils.hasText(request.getSourceLocation()) || !StringUtils.hasText(request.getTargetLocation())) {
                throw new BusinessException("源位置和目标位置不能为空");
            }
            // 源位置和目标位置不能相同，否则移动没有意义。
            if (request.getSourceLocation().equals(request.getTargetLocation())) {
                throw new BusinessException("源位置和目标位置不能相同");
            }
            // 移动数量必须为正数。
            if (request.getQuantity() == null || request.getQuantity() <= 0) {
                throw new BusinessException("移动数量必须大于0");
            }

            // 查询成品，确保移动目标存在。
            FinishedProduct product = finishedProductRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("成品不存在"));

            // 没有库位明细时无法执行跨库移动。
            if (product.getLocations() == null || product.getLocations().isEmpty()) {
                throw new BusinessException("该成品暂无存放位置记录，无法执行跨库移动");
            }

            // 查找源库位。
            LocationInfo sourceLoc = product.getLocations().stream()
                    .filter(l -> request.getSourceLocation().equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 源库位不存在时中断移动。
            if (sourceLoc == null) {
                throw new BusinessException("源位置不存在: " + request.getSourceLocation());
            }
            // 源库位库存不足时中断移动。
            if (sourceLoc.getQuantity() < request.getQuantity()) {
                throw new BusinessException(String.format(
                        "源位置 %s 库存不足，当前可用: %d，请求移动: %d",
                        request.getSourceLocation(), sourceLoc.getQuantity(), request.getQuantity()));
            }

            // 从源库位扣减移动数量。
            sourceLoc.setQuantity(sourceLoc.getQuantity() - request.getQuantity());
            log.info("源位置 {} 减少后剩余: {}", sourceLoc.getLocation(), sourceLoc.getQuantity());

            // 查找目标库位。
            LocationInfo targetLoc = product.getLocations().stream()
                    .filter(l -> request.getTargetLocation().equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            // 目标库位已存在时直接累加数量。
            if (targetLoc != null) {
                targetLoc.setQuantity(targetLoc.getQuantity() + request.getQuantity());
                log.info("目标位置 {} 已存在，增加后: {}", targetLoc.getLocation(), targetLoc.getQuantity());
            } else {
                // 目标库位不存在时创建新库位。
                LocationInfo newTarget = new LocationInfo();
                newTarget.setLocation(request.getTargetLocation());
                newTarget.setQuantity(request.getQuantity());
                newTarget.setCreatedAt(new Date());
                product.getLocations().add(newTarget);
                log.info("创建新目标位置 {}: {}", request.getTargetLocation(), request.getQuantity());
            }

            // 清理移动后数量为 0 的源库位。
            product.getLocations().removeIf(l -> l.getQuantity() <= 0);

            // 重新汇总成品总库存。
            recalculateFinishedProductQuantity(product);

            // 保存移动后的成品数据。
            FinishedProduct saved = finishedProductRepository.save(product);

            // 记录跨库移动完成日志。
            log.info("跨库移动完成 - 成品ID: {}, 新总量: {}, 剩余位置数: {}",
                    saved.getId(), saved.getQuantity(),
                    saved.getLocations() != null ? saved.getLocations().size() : 0);

            // 返回移动后的成品视图。
            return convertToFinishedProductVO(saved);
        }

    

    

        /**
         * 按先进先出（FIFO）原则扣减原材料库存，优先从最早入库的位置扣减
         * <p>
         * 该方法支持总库存扣减和库位 FIFO 扣减两种数据结构，扣减后会记录出库流水并按阈值创建库存预警。
         * </p>
         *
         * @param materialId 原材料ID
         * @param quantity 需要扣减的数量
         * @param reason 扣减原因
         * @throws BusinessException 如果原材料不存在、库存不足或并发扣减失败时抛出业务异常
         */
        @Override
        public void fifoDeductRawMaterial(String materialId, int quantity, String reason) {
            // 先读取原材料，用于判断是否只有总库存而没有库位库存。
            RawMaterial totalOnlyMaterial = rawMaterialRepository.findById(materialId)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));
            // 没有库位明细时走总库存原子扣减分支。
            if (totalOnlyMaterial.getLocations() == null || totalOnlyMaterial.getLocations().isEmpty()) {
                // 原子扣减总库存，最低库存限制为 0。
                boolean deducted = mongoAtomicOpsService.changeRawMaterialQuantity(materialId, -quantity, 0);
                // 扣减失败表示库存不足或发生并发修改。
                if (!deducted) {
                    throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
                }
                // 同步当前内存对象数量，用于流水和预警判断。
                totalOnlyMaterial.setQuantity((totalOnlyMaterial.getQuantity() != null ? totalOnlyMaterial.getQuantity() : 0) - quantity);

                // 创建总库存扣减流水记录。
                InventoryRecord record = new InventoryRecord();
                record.setInventoryType("OUT");
                record.setItemType("RAW_MATERIAL");
                record.setItemId(materialId);
                record.setItemName(totalOnlyMaterial.getName());
                record.setQuantity(-quantity);
                record.setOperator("SYSTEM");
                record.setOperatorName("系统自动");
                record.setReason(reason + " [FIFO:TOTAL(" + quantity + ")]");
                inventoryRecordRepository.save(record);

                // 扣减后低于阈值则创建原材料预警。
                if (totalOnlyMaterial.getAlertThreshold() != null
                        && totalOnlyMaterial.getQuantity() <= totalOnlyMaterial.getAlertThreshold()) {
                    createAlertIfNeeded("RAW_MATERIAL", totalOnlyMaterial.getId(), totalOnlyMaterial.getName(),
                            totalOnlyMaterial.getQuantity(), totalOnlyMaterial.getAlertThreshold());
                }
                // 总库存分支完成后直接返回。
                return;
            }
            // 记录库位 FIFO 扣减入口日志。
            log.info("FIFO扣减原材料 - ID: {}, 扣减数量: {}, 原因: {}", materialId, quantity, reason);

            // 重新读取原材料，确保库位扣减使用最新数据。
            RawMaterial material = rawMaterialRepository.findById(materialId)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 库位 FIFO 分支必须有库位明细。
            if (material.getLocations() == null || material.getLocations().isEmpty()) {
                throw new BusinessException("该原材料暂无存放位置记录，无法执行FIFO扣减");
            }

            // 复制库位并按创建时间升序排序，越早创建越先扣减。
            List<LocationInfo> sortedLocations = new ArrayList<>(material.getLocations());
            sortedLocations.sort((a, b) -> {
                Date aTime = a.getCreatedAt() != null ? a.getCreatedAt() : new Date(0);
                Date bTime = b.getCreatedAt() != null ? b.getCreatedAt() : new Date(0);
                return aTime.compareTo(bTime);
            });

            // 记录剩余待扣减数量。
            int remaining = quantity;
            // 收集 FIFO 扣减明细。
            StringBuilder deductionLog = new StringBuilder();

            // 按 FIFO 顺序逐个库位扣减。
            for (LocationInfo loc : sortedLocations) {
                // 需求已扣满时提前结束。
                if (remaining <= 0) break;

                // 空数量按 0 处理。
                int available = loc.getQuantity() != null ? loc.getQuantity() : 0;
                // 当前库位无库存时跳过。
                if (available <= 0) continue;

                // 本次扣减数量取当前库位可用量和剩余需求中的较小值。
                int deduct = Math.min(available, remaining);
                // 更新库位库存。
                loc.setQuantity(available - deduct);
                // 更新剩余待扣减数量。
                remaining -= deduct;

                // 记录本次库位扣减日志和流水明细。
                log.info("FIFO扣减 - 位置: {}, 原库存: {}, 扣减: {}, 剩余: {}",
                        loc.getLocation(), available, deduct, loc.getQuantity());
                deductionLog.append(loc.getLocation()).append("(").append(deduct).append(")");
            }

            // 所有库位扣完仍不满足需求时抛出库存不足异常。
            if (remaining > 0) {
                throw new BusinessException(String.format(
                        "FIFO扣减失败：位置总库存不足，需扣减 %d，实际可扣减 %d",
                        quantity, quantity - remaining));
            }

            // 清理扣减后库存为 0 的库位。
            material.getLocations().removeIf(l -> l.getQuantity() <= 0);

            // 重新汇总原材料总库存并保存。
            recalculateRawMaterialQuantity(material);
            saveRawMaterialWithLegacyVersionRetry(material);

            // 创建库位 FIFO 扣减流水记录。
            InventoryRecord record = new InventoryRecord();
            record.setInventoryType("OUT");
            record.setItemType("RAW_MATERIAL");
            record.setItemId(materialId);
            record.setItemName(material.getName());
            record.setQuantity(-quantity);
            record.setOperator("SYSTEM");
            record.setOperatorName("系统自动");
            record.setReason(appendFifoDetail(reason, deductionLog.toString()));
            inventoryRecordRepository.save(record);

            // 扣减后低于阈值则创建原材料预警。
            if (material.getAlertThreshold() != null && material.getQuantity() <= material.getAlertThreshold()) {
                createAlertIfNeeded("RAW_MATERIAL", material.getId(), material.getName(),
                        material.getQuantity(), material.getAlertThreshold());
            }

            // 记录 FIFO 扣减完成日志。
            log.info("FIFO扣减完成 - 原材料ID: {}, 新总量: {}, 扣减详情: {}",
                    material.getId(), material.getQuantity(), deductionLog);
        }

    

        /**
         * 按先进先出（FIFO）原则扣减成品库存，优先从最早入库的位置扣减
         * <p>
         * 该方法支持总库存扣减和库位 FIFO 扣减两种数据结构，扣减后会记录出库流水并按阈值创建库存预警。
         * </p>
         *
         * @param finishedProductId 成品ID
         * @param quantity 需要扣减的数量
         * @param reason 扣减原因
         * @throws BusinessException 如果成品不存在、库存不足或并发扣减失败时抛出业务异常
         */
        @Override
        public void fifoDeductFinishedProduct(String finishedProductId, int quantity, String reason) {
            // 先读取成品，用于判断是否只有总库存而没有库位库存。
            FinishedProduct totalOnlyProduct = finishedProductRepository.findById(finishedProductId)
                    .orElseThrow(() -> new BusinessException("成品不存在"));
            // 没有库位明细时走总库存原子扣减分支。
            if (totalOnlyProduct.getLocations() == null || totalOnlyProduct.getLocations().isEmpty()) {
                // 原子扣减总库存，最低库存限制为 0。
                boolean deducted = mongoAtomicOpsService.changeFinishedProductQuantity(finishedProductId, -quantity, 0);
                // 扣减失败表示库存不足或发生并发修改。
                if (!deducted) {
                    throw new BusinessException("库存不足或已被其他操作更新，请刷新后重试");
                }
                // 同步当前内存对象数量，用于流水和预警判断。
                int available = totalOnlyProduct.getQuantity() != null ? totalOnlyProduct.getQuantity() : 0;
                totalOnlyProduct.setQuantity(available - quantity);

                // 创建总库存扣减流水记录。
                InventoryRecord record = new InventoryRecord();
                record.setInventoryType("OUT");
                record.setItemType("FINISHED_PRODUCT");
                record.setItemId(finishedProductId);
                record.setItemName(totalOnlyProduct.getName());
                record.setQuantity(-quantity);
                record.setOperator("SYSTEM");
                record.setOperatorName("系统自动");
                record.setReason(reason + " [FIFO:TOTAL(" + quantity + ")]");
                inventoryRecordRepository.save(record);

                // 扣减后低于阈值则创建成品预警。
                if (totalOnlyProduct.getAlertThreshold() != null
                        && totalOnlyProduct.getQuantity() <= totalOnlyProduct.getAlertThreshold()) {
                    createAlertIfNeeded("FINISHED_PRODUCT", totalOnlyProduct.getId(), totalOnlyProduct.getName(),
                            totalOnlyProduct.getQuantity(), totalOnlyProduct.getAlertThreshold());
                }
                // 总库存分支完成后直接返回。
                return;
            }
            // 记录库位 FIFO 扣减入口日志。
            log.info("FIFO扣减成品 - ID: {}, 扣减数量: {}, 原因: {}", finishedProductId, quantity, reason);

            // 重新读取成品，确保库位扣减使用最新数据。
            FinishedProduct product = finishedProductRepository.findById(finishedProductId)
                    .orElseThrow(() -> new BusinessException("成品不存在"));

            // 判断是否存在库位库存。
            boolean hasLocationInventory = product.getLocations() != null && !product.getLocations().isEmpty();
            // 收集 FIFO 扣减明细。
            StringBuilder deductionLog = new StringBuilder();
            // 没有库位库存时，按总库存直接扣减。
            if (!hasLocationInventory) {
                // 空数量按 0 处理。
                int available = product.getQuantity() != null ? product.getQuantity() : 0;
                // 总库存不足时中断扣减。
                if (available < quantity) {
                    throw new BusinessException(String.format(
                            "FIFO扣减失败：成品库存不足，需扣减 %d，实际可扣减 %d",
                            quantity, available));
                }
                // 扣减总库存并记录总库存扣减明细。
                product.setQuantity(available - quantity);
                deductionLog.append("总库存(").append(quantity).append(")");
            } else {
                // 复制库位并按创建时间升序排序，越早创建越先扣减。
                List<LocationInfo> sortedLocations = new ArrayList<>(product.getLocations());
                sortedLocations.sort((a, b) -> {
                    Date aTime = a.getCreatedAt() != null ? a.getCreatedAt() : new Date(0);
                    Date bTime = b.getCreatedAt() != null ? b.getCreatedAt() : new Date(0);
                    return aTime.compareTo(bTime);
                });

                // 记录剩余待扣减数量。
                int remaining = quantity;
                // 按 FIFO 顺序逐个库位扣减。
                for (LocationInfo loc : sortedLocations) {
                    // 需求已扣满时提前结束。
                    if (remaining <= 0) {
                        break;
                    }

                    // 空数量按 0 处理。
                    int available = loc.getQuantity() != null ? loc.getQuantity() : 0;
                    // 当前库位无库存时跳过。
                    if (available <= 0) {
                        continue;
                    }

                    // 本次扣减数量取当前库位可用量和剩余需求中的较小值。
                    int deduct = Math.min(available, remaining);
                    // 更新库位库存。
                    loc.setQuantity(available - deduct);
                    // 更新剩余待扣减数量。
                    remaining -= deduct;

                    // 记录本次库位扣减日志和流水明细。
                    log.info("FIFO扣减成品 - 位置: {}, 原库存: {}, 扣减: {}, 剩余: {}",
                            loc.getLocation(), available, deduct, loc.getQuantity());
                    deductionLog.append(loc.getLocation()).append("(").append(deduct).append(")");
                }

                // 所有库位扣完仍不满足需求时抛出库存不足异常。
                if (remaining > 0) {
                    throw new BusinessException(String.format(
                            "FIFO扣减失败：位置总库存不足，需扣减 %d，实际可扣减 %d",
                            quantity, quantity - remaining));
                }

                // 清理扣减后库存为空或小于等于 0 的库位。
                product.getLocations().removeIf(l -> l.getQuantity() == null || l.getQuantity() <= 0);
            }

            // 有库位库存时重新汇总成品总库存。
            if (hasLocationInventory) {
                recalculateFinishedProductQuantity(product);
            }
            // 保存扣减后的成品库存。
            finishedProductRepository.save(product);

            // 创建成品 FIFO 扣减流水记录。
            InventoryRecord record = new InventoryRecord();
            record.setInventoryType("OUT");
            record.setItemType("FINISHED_PRODUCT");
            record.setItemId(finishedProductId);
            record.setItemName(product.getName());
            record.setQuantity(-quantity);
            record.setOperator("SYSTEM");
            record.setOperatorName("系统自动");
            record.setReason(reason + " [FIFO:" + deductionLog + "]");
            inventoryRecordRepository.save(record);

            // 扣减后低于阈值则创建成品预警。
            if (product.getAlertThreshold() != null && product.getQuantity() <= product.getAlertThreshold()) {
                createAlertIfNeeded("FINISHED_PRODUCT", product.getId(), product.getName(),
                        product.getQuantity(), product.getAlertThreshold());
            }

            // 记录 FIFO 扣减完成日志。
            log.info("FIFO扣减成品完成 - 成品ID: {}, 新总量: {}, 扣减详情: {}",
                    product.getId(), product.getQuantity(), deductionLog);
        }

    

        /**
         * 按先进先出（FIFO）原则扣减原材料库存，并生成扣减凭证记录各位置扣减详情
         * <p>
         * 该方法在扣减前保存库位快照，扣减后通过前后库存差异生成可用于回滚的扣减凭证。
         * </p>
         *
         * @param materialId 原材料ID
         * @param quantity 需要扣减的数量
         * @param reason 扣减原因
         * @return 库存扣减凭证，包含各位置的扣减明细
         * @throws BusinessException 如果原材料不存在或库存扣减失败时抛出业务异常
         */
        @Override
        public InventoryDeductionReceipt fifoDeductRawMaterialWithReceipt(String materialId, int quantity, String reason) {
            // 扣减前读取原材料，用于保存名称和库位快照。
            RawMaterial beforeDeduction = rawMaterialRepository.findById(materialId)
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 保存物品名称，用于后续凭证和回滚流水。
            String itemName = beforeDeduction.getName();
            // 深拷贝扣减前库位列表，避免扣减过程修改原引用。
            List<LocationInfo> beforeLocations = copyLocationInfos(beforeDeduction.getLocations());
            // 记录当前是否为总库存扣减模式。
            boolean totalOnly = beforeLocations.isEmpty();

            // 执行实际 FIFO 扣减。
            fifoDeductRawMaterial(materialId, quantity, reason);

            // 总库存模式不需要位置明细，直接返回总库存扣减凭证。
            if (totalOnly) {
                return new InventoryDeductionReceipt(
                        "RAW_MATERIAL",
                        materialId,
                        itemName,
                        quantity,
                        true,
                        new ArrayList<>());
            }

            // 扣减后重新读取原材料，和扣减前快照做差。
            RawMaterial afterDeduction = rawMaterialRepository.findById(materialId).orElse(beforeDeduction);
            // 收集每个库位实际扣减的数量。
            List<InventoryDeductionReceipt.LocationDeduction> locationDeductions = new ArrayList<>();

            // 遍历扣减前库位，计算每个库位的前后数量差。
            for (LocationInfo beforeLocation : beforeLocations) {
                // 扣减前数量为空时按 0 处理。
                int beforeQuantity = beforeLocation.getQuantity() != null ? beforeLocation.getQuantity() : 0;
                // 查询扣减后同名库位的剩余数量，库位已移除时按 0 处理。
                int afterQuantity = afterDeduction.getLocations() == null ? 0 : afterDeduction.getLocations().stream()
                        .filter(location -> Objects.equals(location.getLocation(), beforeLocation.getLocation()))
                        .map(LocationInfo::getQuantity)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(0);

                // 前后差值即该库位本次实际扣减数量。
                int deducted = beforeQuantity - afterQuantity;
                // 只记录发生了正向扣减的库位。
                if (deducted > 0) {
                    locationDeductions.add(new InventoryDeductionReceipt.LocationDeduction(
                            beforeLocation.getLocation(),
                            deducted,
                            beforeLocation.getCreatedAt()));
                }
            }

            // 返回包含库位扣减明细的原材料扣减凭证。
            return new InventoryDeductionReceipt(
                    "RAW_MATERIAL",
                    materialId,
                    itemName,
                    quantity,
                    false,
                    locationDeductions);
        }

    

        /**
         * 按先进先出（FIFO）原则扣减成品库存，并生成扣减凭证（当前返回null，待完善）
         * <p>
         * 该方法目前复用成品 FIFO 扣减逻辑执行扣减，暂未生成成品位置扣减凭证。
         * </p>
         *
         * @param finishedProductId 成品ID
         * @param quantity 需要扣减的数量
         * @param reason 扣减原因
         * @return 库存扣减凭证，包含各位置的扣减明细（当前实现返回null）
         */
        @Override
        public InventoryDeductionReceipt fifoDeductFinishedProductWithReceipt(String finishedProductId, int quantity, String reason) {
            // 执行成品 FIFO 扣减。
            fifoDeductFinishedProduct(finishedProductId, quantity, reason);
            // 当前实现尚未返回成品扣减凭证。
            return null;
        }


        /**
         * 恢复库存扣减操作，根据扣减凭证将已扣减的库存重新入库
         * <p>
         * 该方法优先按原材料库位扣减凭证精确恢复；其他场景退化为普通入库恢复。
         * </p>
         *
         * @param receipt 库存扣减凭证
         * @param reason 恢复原因
         */
        @Override
        public void restoreInventoryDeduction(InventoryDeductionReceipt receipt, String reason) {
            // 凭证为空或物品类型为空时无法恢复，直接忽略。
            if (receipt == null || !StringUtils.hasText(receipt.getItemType())) {
                return;
            }
            // 原材料且包含库位扣减明细时，按库位精确恢复。
            if ("RAW_MATERIAL".equals(receipt.getItemType())
                    && !receipt.isTotalOnly()
                    && receipt.getLocationDeductions() != null
                    && !receipt.getLocationDeductions().isEmpty()) {
                restoreRawMaterialDeduction(receipt, reason);
                return;
            }
            // 其他情况构造普通入库请求，用库存入库流程恢复总量。
            StockInOutRequest request = new StockInOutRequest();
            request.setItemType(receipt.getItemType());
            request.setItemId(receipt.getItemId());
            request.setQuantity(receipt.getQuantity());
            request.setReason(reason);
            stockIn(request, "system");
        }


        /**
         * 复制位置信息列表，创建深拷贝副本
         * <p>
         * 该方法用于扣减前保存库位快照，避免后续库存修改影响凭证差异计算。
         * </p>
         *
         * @param locations 原始位置信息列表
         * @return 复制后的位置信息列表
         */
        private List<LocationInfo> copyLocationInfos(List<LocationInfo> locations) {
            // 初始化复制结果列表。
            List<LocationInfo> copies = new ArrayList<>();
            // 原始列表为空时返回空列表。
            if (locations == null) {
                return copies;
            }
            // 遍历原始库位并逐条复制。
            for (LocationInfo location : locations) {
                // 跳过空库位对象。
                if (location == null) {
                    continue;
                }
                // 创建新的 LocationInfo，避免引用原对象。
                copies.add(new LocationInfo(
                        location.getLocation(),
                        location.getQuantity(),
                        location.getCreatedAt()));
            }
            // 返回深拷贝后的库位列表。
            return copies;
        }


        /**
         * 恢复原材料扣减操作，根据扣减凭证将各位置的库存恢复到对应位置
         * <p>
         * 该方法根据扣减凭证逐个恢复原材料库位库存，重新计算总库存，并记录恢复入库流水。
         * </p>
         *
         * @param receipt 库存扣减凭证
         * @param reason 恢复原因
         */
        private void restoreRawMaterialDeduction(InventoryDeductionReceipt receipt, String reason) {
            // 查询原材料，确保恢复目标存在。
            RawMaterial material = rawMaterialRepository.findById(receipt.getItemId())
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            // 库位列表为空时先初始化，便于追加恢复库位。
            if (material.getLocations() == null) {
                material.setLocations(new ArrayList<>());
            }

            // 收集恢复明细，用于库存流水原因。
            StringBuilder deductionLog = new StringBuilder();
            // 统计实际恢复总数量。
            int restoredQuantity = 0;

            // 遍历凭证中的库位扣减明细。
            for (InventoryDeductionReceipt.LocationDeduction locationDeduction : receipt.getLocationDeductions()) {
                // 跳过无效的库位扣减明细。
                if (locationDeduction == null
                        || !StringUtils.hasText(locationDeduction.getLocation())
                        || locationDeduction.getQuantity() == null
                        || locationDeduction.getQuantity() <= 0) {
                    continue;
                }

                // 将指定库位的扣减数量恢复回原材料库存。
                restoreRawMaterialLocation(material, locationDeduction);
                // 累加实际恢复数量。
                restoredQuantity += locationDeduction.getQuantity();
                // 拼接恢复明细。
                deductionLog.append(locationDeduction.getLocation())
                        .append("(")
                        .append(locationDeduction.getQuantity())
                        .append(")");
            }

            // 恢复后重新汇总原材料总库存并保存。
            recalculateRawMaterialQuantity(material);
            saveRawMaterialWithLegacyVersionRetry(material);

            // 创建库存恢复入库流水。
            InventoryRecord record = new InventoryRecord();
            record.setInventoryType("IN");
            record.setItemType("RAW_MATERIAL");
            record.setItemId(receipt.getItemId());
            record.setItemName(StringUtils.hasText(receipt.getItemName()) ? receipt.getItemName() : material.getName());
            record.setQuantity(restoredQuantity > 0 ? restoredQuantity : receipt.getQuantity());
            record.setOperator("SYSTEM");
            record.setOperatorName("系统自动");
            record.setReason(appendFifoDetail(reason, deductionLog.toString()));
            inventoryRecordRepository.save(record);
        }


        /**
         * 恢复原材料到指定位置的库存，如果位置已存在则累加数量
         * <p>
         * 该方法用于库存回滚时恢复某个库位的数量，库位不存在则重新创建该库位。
         * </p>
         *
         * @param material 原材料对象
         * @param locationDeduction 位置扣减信息
         */
        private void restoreRawMaterialLocation(RawMaterial material, InventoryDeductionReceipt.LocationDeduction locationDeduction) {
            // 查找是否已经存在需要恢复的库位。
            LocationInfo existing = material.getLocations().stream()
                    .filter(location -> Objects.equals(location.getLocation(), locationDeduction.getLocation()))
                    .findFirst()
                    .orElse(null);

            // 库位已存在时，直接累加恢复数量。
            if (existing != null) {
                existing.setQuantity((existing.getQuantity() != null ? existing.getQuantity() : 0) + locationDeduction.getQuantity());
                // 原库位缺少创建时间时，使用凭证中保存的创建时间补齐。
                if (existing.getCreatedAt() == null && locationDeduction.getCreatedAt() != null) {
                    existing.setCreatedAt(locationDeduction.getCreatedAt());
                }
                // 已恢复到现有库位，直接返回。
                return;
            }

            // 库位不存在时，创建新的恢复库位。
            LocationInfo restoredLocation = new LocationInfo();
            restoredLocation.setLocation(locationDeduction.getLocation());
            restoredLocation.setQuantity(locationDeduction.getQuantity());
            restoredLocation.setCreatedAt(locationDeduction.getCreatedAt() != null ? locationDeduction.getCreatedAt() : new Date());
            // 将恢复库位追加到原材料库位列表。
            material.getLocations().add(restoredLocation);
        }


        /**
         * 将生产计划状态代码转换为中文描述
         * <p>
         * 该方法用于错误提示或界面展示，将生产计划状态枚举值转换为中文文案。
         * </p>
         *
         * @param status 状态代码（PENDING、APPROVED、IN_PROGRESS、COMPLETED、CANCELLED）
         * @return 状态中文描述
         */
        private String getPlanStatusText(String status) {
            // 状态为空时返回兜底文案。
            if (!StringUtils.hasText(status)) {
                return "未知状态";
            }
            // 根据状态代码返回对应中文描述。
            switch (status) {
                case "PENDING":
                    return "待审批";
                case "APPROVED":
                    return "已审批";
                case "IN_PROGRESS":
                    return "进行中";
                case "COMPLETED":
                    return "已完成";
                case "CANCELLED":
                    return "已取消";
                default:
                    // 未知状态直接返回原始状态码，便于排查新增状态。
                    return status;
            }
        }


        /**
         * 将物品类型代码转换为中文描述
         * <p>
         * 该方法用于错误提示或界面展示，将库存物品类型枚举值转换为中文文案。
         * </p>
         *
         * @param itemType 物品类型代码（RAW_MATERIAL、FINISHED_PRODUCT）
         * @return 物品类型中文描述
         */
        private String getItemTypeText(String itemType) {
            // 类型为空时返回兜底文案。
            if (!StringUtils.hasText(itemType)) {
                return "未知类型";
            }
            // 根据物品类型代码返回中文描述。
            switch (itemType) {
                case "RAW_MATERIAL":
                    return "原材料";
                case "FINISHED_PRODUCT":
                    return "成品";
                default:
                    // 未知类型直接返回原始类型码，便于排查新增类型。
                    return itemType;
            }
        }

    
}
