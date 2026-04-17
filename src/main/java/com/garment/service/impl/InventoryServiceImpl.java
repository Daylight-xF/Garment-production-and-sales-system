package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.*;
import com.garment.repository.*;
import com.garment.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class InventoryServiceImpl implements InventoryService {

    private final RawMaterialRepository rawMaterialRepository;
    private final FinishedProductRepository finishedProductRepository;
    private final ProductDefinitionRepository productDefinitionRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryAlertRepository inventoryAlertRepository;
    private final UserRepository userRepository;

    public InventoryServiceImpl(RawMaterialRepository rawMaterialRepository,
                                 FinishedProductRepository finishedProductRepository,
                                 ProductDefinitionRepository productDefinitionRepository,
                                 ProductionPlanRepository productionPlanRepository,
                                 InventoryRecordRepository inventoryRecordRepository,
                                 InventoryAlertRepository inventoryAlertRepository,
                                 UserRepository userRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.finishedProductRepository = finishedProductRepository;
        this.productDefinitionRepository = productDefinitionRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryAlertRepository = inventoryAlertRepository;
        this.userRepository = userRepository;
    }

    @Override
    public Page<RawMaterialVO> getRawMaterialList(String name, String category, Pageable pageable) {
        List<RawMaterial> all = rawMaterialRepository.findAll();
        List<RawMaterial> filtered = all.stream()
                .filter(m -> !StringUtils.hasText(name) || (m.getName() != null && m.getName().contains(name)))
                .filter(m -> !StringUtils.hasText(category) || category.equals(m.getCategory()))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<RawMaterial> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<RawMaterialVO> voList = pageContent.stream()
                .map(this::convertToRawMaterialVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public RawMaterialVO getRawMaterialById(String id) {
        RawMaterial material = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("原材料不存在"));
        return convertToRawMaterialVO(material);
    }

    @Override
    public RawMaterialVO createRawMaterial(RawMaterialCreateRequest request) {
        RawMaterial material = new RawMaterial();
        material.setName(request.getName());
        material.setCategory(request.getCategory());
        material.setSpecification(request.getSpecification());
        material.setUnit(request.getUnit());
        material.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        material.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 0);
        material.setSupplier(request.getSupplier());

        if (StringUtils.hasText(request.getLocation())) {
            List<LocationInfo> locations = new ArrayList<>();
            LocationInfo info = new LocationInfo();
            info.setLocation(request.getLocation());
            info.setQuantity(material.getQuantity() != null ? material.getQuantity() : 0);
            info.setCreatedAt(new Date());
            locations.add(info);
            material.setLocations(locations);
        }

        material.setPrice(request.getPrice());
        material.setDescription(request.getDescription());

        RawMaterial saved = rawMaterialRepository.save(material);
        return convertToRawMaterialVO(saved);
    }

    @Override
    public RawMaterialVO updateRawMaterial(String id, RawMaterialUpdateRequest request) {
        RawMaterial material = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("原材料不存在"));

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
        if (StringUtils.hasText(request.getLocation())) {
            List<LocationInfo> locations = material.getLocations();
            if (locations == null) {
                locations = new ArrayList<>();
            }
            LocationInfo existing = locations.stream()
                    .filter(l -> request.getLocation().equals(l.getLocation()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setQuantity(material.getQuantity() != null ? material.getQuantity() : 0);
            } else {
                LocationInfo info = new LocationInfo();
                info.setLocation(request.getLocation());
                info.setQuantity(material.getQuantity() != null ? material.getQuantity() : 0);
                info.setCreatedAt(new Date());
                locations.add(info);
            }
            material.setLocations(locations);
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

        RawMaterial saved = rawMaterialRepository.save(material);
        return convertToRawMaterialVO(saved);
    }

    @Override
    public void deleteRawMaterial(String id) {
        if (!rawMaterialRepository.existsById(id)) {
            throw new BusinessException("原材料不存在");
        }
        rawMaterialRepository.deleteById(id);
    }

    @Override
    public Page<FinishedProductVO> getFinishedProductList(String keyword, String category, Pageable pageable) {
        List<FinishedProduct> all = finishedProductRepository.findAll();
        List<FinishedProduct> filtered = all.stream()
                .filter(p -> !StringUtils.hasText(keyword)
                        || (p.getBatchNo() != null && p.getBatchNo().contains(keyword))
                        || (p.getName() != null && p.getName().contains(keyword)))
                .filter(p -> !StringUtils.hasText(category) || category.equals(p.getCategory()))
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<FinishedProduct> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<FinishedProductVO> voList = pageContent.stream()
                .map(this::convertToFinishedProductVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public FinishedProductVO getFinishedProductById(String id) {
        FinishedProduct product = finishedProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException("成品不存在"));
        return convertToFinishedProductVO(product);
    }

    @Override
    public FinishedProductVO createFinishedProduct(FinishedProductCreateRequest request) {
        FinishedProduct product = new FinishedProduct();
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setColor(request.getColor());
        product.setSize(request.getSize());
        product.setUnit(request.getUnit());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        product.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 0);
        product.setPrice(request.getPrice());
        product.setCostPrice(request.getCostPrice());
        product.setDescription(request.getDescription());

        FinishedProduct saved = finishedProductRepository.save(product);
        return convertToFinishedProductVO(saved);
    }

    @Override
    public FinishedProductVO updateFinishedProduct(String id, FinishedProductUpdateRequest request) {
        FinishedProduct product = finishedProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException("成品不存在"));

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

        FinishedProduct saved = finishedProductRepository.save(product);
        return convertToFinishedProductVO(saved);
    }

    @Override
    public void deleteFinishedProduct(String id) {
        if (!finishedProductRepository.existsById(id)) {
            throw new BusinessException("成品不存在");
        }
        finishedProductRepository.deleteById(id);
    }

    @Override
    public InventoryRecordVO stockIn(StockInOutRequest request, String operatorId) {
        log.info("开始入库操作 - 类型: {}, 物品ID: {}, 数量: {}, 操作人: {}",
                request.getItemType(), request.getItemId(), request.getQuantity(), operatorId);

        String itemName = "";
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
                material.setQuantity(material.getQuantity() + request.getQuantity());
            }

            recalculateRawMaterialQuantity(material);
            rawMaterialRepository.save(material);
            itemName = material.getName();
            log.info("原材料入库成功 - 名称: {}, 新库存: {}", itemName, material.getQuantity());
        } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
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
                throw new BusinessException("生产计划尚未完成，无法入库。当前状态: " + plan.getStatus());
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
            log.error("无效的物品类型: {}", request.getItemType());
            throw new BusinessException("无效的物品类型: " + request.getItemType());
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
        record.setReason(request.getReason());

        InventoryRecord saved = inventoryRecordRepository.save(record);
        log.info("入库记录已保存 - 记录ID: {}, 物品: {}, 数量: {}",
                saved.getId(), itemName, request.getQuantity());

        return convertToInventoryRecordVO(saved);
    }

    @Override
    public InventoryRecordVO stockOut(StockInOutRequest request, String operatorId) {
        log.info("开始出库操作 - 类型: {}, 物品ID: {}, 数量: {}, 操作人: {}",
                request.getItemType(), request.getItemId(), request.getQuantity(), operatorId);

        String itemName = "";
        if ("RAW_MATERIAL".equals(request.getItemType())) {
            RawMaterial material = rawMaterialRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("原材料不存在"));

            String location = extractLocation(request.getReason());
            if (StringUtils.hasText(location) && material.getLocations() != null && !material.getLocations().isEmpty()) {
                LocationInfo targetLoc = material.getLocations().stream()
                        .filter(l -> location.equals(l.getLocation()))
                        .findFirst()
                        .orElse(null);
                if (targetLoc == null) {
                    throw new BusinessException("出库位置不存在: " + location);
                }
                if (targetLoc.getQuantity() < request.getQuantity()) {
                    throw new BusinessException(String.format(
                            "位置 %s 库存不足，当前可用: %d，请求出库: %d",
                            location, targetLoc.getQuantity(), request.getQuantity()));
                }
                log.info("原材料出库 - 名称: {}, 位置: {}, 出库数量: {}, 位置原库存: {}",
                        material.getName(), location, request.getQuantity(), targetLoc.getQuantity());
                targetLoc.setQuantity(targetLoc.getQuantity() - request.getQuantity());
                material.getLocations().removeIf(l -> l.getQuantity() <= 0);
            } else {
                if (material.getQuantity() < request.getQuantity()) {
                    throw new BusinessException("库存不足，当前库存：" + material.getQuantity());
                }
                material.setQuantity(material.getQuantity() - request.getQuantity());
            }

            recalculateRawMaterialQuantity(material);
            rawMaterialRepository.save(material);
            itemName = material.getName();

            if (material.getAlertThreshold() != null && material.getQuantity() <= material.getAlertThreshold()) {
                createAlertIfNeeded("RAW_MATERIAL", material.getId(), material.getName(),
                        material.getQuantity(), material.getAlertThreshold());
            }

            log.info("原材料出库成功 - 名称: {}, 原材料ID: {}, 出库数量: {}, 新库存: {}, 剩余位置数: {}",
                    itemName, material.getId(), request.getQuantity(), material.getQuantity(),
                    material.getLocations() != null ? material.getLocations().size() : 0);
        } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
            FinishedProduct product = finishedProductRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("成品不存在"));

            String location = extractLocation(request.getReason());
            if (StringUtils.hasText(location) && product.getLocations() != null && !product.getLocations().isEmpty()) {
                LocationInfo targetLoc = product.getLocations().stream()
                        .filter(l -> location.equals(l.getLocation()))
                        .findFirst()
                        .orElse(null);
                if (targetLoc == null) {
                    throw new BusinessException("出库位置不存在: " + location);
                }
                if (targetLoc.getQuantity() < request.getQuantity()) {
                    throw new BusinessException(String.format(
                            "位置 %s 库存不足，当前可用: %d，请求出库: %d",
                            location, targetLoc.getQuantity(), request.getQuantity()));
                }
                log.info("成品出库 - 名称: {}, 位置: {}, 出库数量: {}, 位置原库存: {}",
                        product.getName(), location, request.getQuantity(), targetLoc.getQuantity());
                targetLoc.setQuantity(targetLoc.getQuantity() - request.getQuantity());
                product.getLocations().removeIf(l -> l.getQuantity() <= 0);
            } else {
                if (product.getQuantity() < request.getQuantity()) {
                    throw new BusinessException("库存不足，当前库存：" + product.getQuantity());
                }
                product.setQuantity(product.getQuantity() - request.getQuantity());
            }

            recalculateFinishedProductQuantity(product);
            finishedProductRepository.save(product);
            itemName = product.getName();

            if (product.getAlertThreshold() != null && product.getQuantity() <= product.getAlertThreshold()) {
                createAlertIfNeeded("FINISHED_PRODUCT", product.getId(), product.getName(),
                        product.getQuantity(), product.getAlertThreshold());
            }

            log.info("成品出库成功 - 名称: {}, 成品ID: {}, 出库数量: {}, 新库存: {}, 剩余位置数: {}",
                    itemName, product.getId(), request.getQuantity(), product.getQuantity(),
                    product.getLocations() != null ? product.getLocations().size() : 0);
        } else {
            throw new BusinessException("无效的物品类型");
        }

        String operatorName = getOperatorName(operatorId);

        InventoryRecord record = new InventoryRecord();
        record.setInventoryType("OUT");
        record.setItemType(request.getItemType());
        record.setItemId(request.getItemId());
        record.setItemName(itemName);
        record.setQuantity(request.getQuantity());
        record.setOperator(operatorId);
        record.setOperatorName(operatorName);
        record.setReason(request.getReason());

        InventoryRecord saved = inventoryRecordRepository.save(record);
        return convertToInventoryRecordVO(saved);
    }

    @Override
    public Page<InventoryRecordVO> getInventoryRecords(String itemType, String itemId, Pageable pageable) {
        List<InventoryRecord> all = inventoryRecordRepository.findAll();
        List<InventoryRecord> filtered = all.stream()
                .filter(r -> !StringUtils.hasText(itemType) || itemType.equals(r.getItemType()))
                .filter(r -> !StringUtils.hasText(itemId) || itemId.equals(r.getItemId()))
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<InventoryRecord> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<InventoryRecordVO> voList = pageContent.stream()
                .map(this::convertToInventoryRecordVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public Page<InventoryAlertVO> getAlerts(String status, Pageable pageable) {
        List<InventoryAlert> all = inventoryAlertRepository.findAll();
        List<InventoryAlert> filtered = all.stream()
                .filter(a -> !StringUtils.hasText(status) || status.equals(a.getStatus()))
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<InventoryAlert> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<InventoryAlertVO> voList = pageContent.stream()
                .map(this::convertToInventoryAlertVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public InventoryAlertVO handleAlert(String id, AlertHandleRequest request) {
        InventoryAlert alert = inventoryAlertRepository.findById(id)
                .orElseThrow(() -> new BusinessException("预警记录不存在"));

        if (!"PENDING".equals(alert.getStatus())) {
            throw new BusinessException("该预警已处理");
        }

        alert.setStatus("HANDLED");
        alert.setHandleTime(new Date());
        alert.setHandleBy(request.getHandleBy());

        InventoryAlert saved = inventoryAlertRepository.save(alert);
        return convertToInventoryAlertVO(saved);
    }

    @Override
    public RawMaterialVO setRawMaterialThreshold(String id, ThresholdRequest request) {
        RawMaterial material = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("原材料不存在"));

        material.setAlertThreshold(request.getAlertThreshold());
        RawMaterial saved = rawMaterialRepository.save(material);
        return convertToRawMaterialVO(saved);
    }

    @Override
    public FinishedProductVO setFinishedProductThreshold(String id, ThresholdRequest request) {
        FinishedProduct product = finishedProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException("成品不存在"));

        product.setAlertThreshold(request.getAlertThreshold());
        FinishedProduct saved = finishedProductRepository.save(product);
        return convertToFinishedProductVO(saved);
    }

    private void createAlertIfNeeded(String itemType, String itemId, String itemName,
                                      Integer currentQuantity, Integer threshold) {
        List<InventoryAlert> existing = inventoryAlertRepository.findAll();
        boolean hasPending = existing.stream()
                .anyMatch(a -> itemType.equals(a.getItemType())
                        && itemId.equals(a.getItemId())
                        && "PENDING".equals(a.getStatus()));

        if (!hasPending) {
            InventoryAlert alert = new InventoryAlert();
            alert.setItemType(itemType);
            alert.setItemId(itemId);
            alert.setItemName(itemName);
            alert.setCurrentQuantity(currentQuantity);
            alert.setThreshold(threshold);
            alert.setStatus("PENDING");
            inventoryAlertRepository.save(alert);
        }
    }

    private String getOperatorName(String operatorId) {
        return userRepository.findById(operatorId)
                .map(User::getRealName)
                .orElse("");
    }

    private FinishedProduct findOrCreateFinishedProduct(ProductionPlan plan) {
        List<FinishedProduct> existing = finishedProductRepository.findAll().stream()
                .filter(product -> matchesFinishedProductIdentity(product, plan))
                .collect(Collectors.toList());

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

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

        return finishedProductRepository.save(newProduct);
    }

    private boolean matchesFinishedProductIdentity(FinishedProduct product, ProductionPlan plan) {
        return sameText(product.getBatchNo(), plan.getBatchNo())
                && sameText(product.getName(), plan.getProductName())
                && sameText(product.getProductCode(), plan.getProductCode())
                && sameText(product.getColor(), plan.getColor())
                && sameText(product.getSize(), plan.getSize());
    }

    private boolean sameText(String left, String right) {
        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String extractLocation(String reason) {
        if (reason == null || !reason.contains("位置:")) {
            return null;
        }
        int startIndex = reason.indexOf("位置:") + 3;
        int endIndex = reason.indexOf(" |", startIndex);
        if (endIndex == -1) {
            endIndex = reason.length();
        }
        return reason.substring(startIndex, endIndex).trim();
    }

    private void recalculateRawMaterialQuantity(RawMaterial material) {
        int total = 0;
        if (material.getLocations() != null) {
            total = material.getLocations().stream()
                    .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                    .sum();
        }
        material.setQuantity(total);
        log.debug("重新计算原材料总库存 - ID: {}, 新总量: {}, 位置数: {}",
                material.getId(), total, material.getLocations() != null ? material.getLocations().size() : 0);
    }

    private void recalculateAndFixQuantity(RawMaterial material, RawMaterialRepository repo) {
        int correctTotal = 0;
        if (material.getLocations() != null) {
            correctTotal = material.getLocations().stream()
                    .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                    .sum();
        }
        if (material.getQuantity() == null || material.getQuantity() != correctTotal) {
            log.warn("检测到原材料数量不一致 - ID: {}, 原始quantity: {}, 正确总量: {}, 自动修正",
                    material.getId(), material.getQuantity(), correctTotal);
            material.setQuantity(correctTotal);
            repo.save(material);
        }
    }

    private void recalculateAndFixFinishedProductQuantity(FinishedProduct product, FinishedProductRepository repo) {
        int correctTotal = 0;
        if (product.getLocations() != null) {
            correctTotal = product.getLocations().stream()
                    .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                    .sum();
        }
        if (product.getQuantity() == null || product.getQuantity() != correctTotal) {
            log.warn("检测到成品数量不一致 - ID: {}, 原始quantity: {}, 正确总量: {}, 自动修正",
                    product.getId(), product.getQuantity(), correctTotal);
            product.setQuantity(correctTotal);
            repo.save(product);
        }
    }

    private void recalculateFinishedProductQuantity(FinishedProduct product) {
        int total = 0;
        if (product.getLocations() != null) {
            total = product.getLocations().stream()
                    .mapToInt(l -> l.getQuantity() != null ? l.getQuantity() : 0)
                    .sum();
        }
        product.setQuantity(total);
        log.debug("重新计算成品总库存 - ID: {}, 新总量: {}, 位置数: {}",
                product.getId(), total, product.getLocations() != null ? product.getLocations().size() : 0);
    }

    private void addOrUpdateLocation(FinishedProduct product, String location, int quantity) {
        if (product.getLocations() == null) {
            product.setLocations(new ArrayList<>());
        }
        LocationInfo existing = product.getLocations().stream()
                .filter(l -> location.equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            LocationInfo newLocation = new LocationInfo();
            newLocation.setLocation(location);
            newLocation.setQuantity(quantity);
            newLocation.setCreatedAt(new Date());
            product.getLocations().add(newLocation);
        }
    }

    private void addOrUpdateRawMaterialLocation(RawMaterial material, String location, int quantity) {
        if (material.getLocations() == null) {
            material.setLocations(new ArrayList<>());
        }
        LocationInfo existing = material.getLocations().stream()
                .filter(l -> location.equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
        } else {
            LocationInfo newLocation = new LocationInfo();
            newLocation.setLocation(location);
            newLocation.setQuantity(quantity);
            newLocation.setCreatedAt(new Date());
            material.getLocations().add(newLocation);
        }
    }

    private RawMaterialVO convertToRawMaterialVO(RawMaterial material) {
        recalculateAndFixQuantity(material, rawMaterialRepository);
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

    private FinishedProductVO convertToFinishedProductVO(FinishedProduct product) {
        recalculateAndFixFinishedProductQuantity(product, finishedProductRepository);
        Double dynamicCostPrice = resolveFinishedProductCostPrice(product.getProductCode());
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

    private Double resolveFinishedProductCostPrice(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return null;
        }

        ProductDefinition definition = productDefinitionRepository.findByProductCode(productCode).orElse(null);
        if (definition == null || definition.getMaterials() == null || definition.getMaterials().isEmpty()) {
            return null;
        }

        double unitCost = 0D;
        boolean hasMatchedMaterial = false;
        for (ProductDefinition.ProductMaterial material : definition.getMaterials()) {
            if (!StringUtils.hasText(material.getMaterialId())) {
                continue;
            }

            RawMaterial rawMaterial = rawMaterialRepository.findById(material.getMaterialId()).orElse(null);
            if (rawMaterial == null || rawMaterial.getPrice() == null || material.getQuantity() == null) {
                continue;
            }

            unitCost += rawMaterial.getPrice() * material.getQuantity();
            hasMatchedMaterial = true;
        }

        return hasMatchedMaterial ? unitCost : null;
    }

    private InventoryRecordVO convertToInventoryRecordVO(InventoryRecord record) {
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

    private InventoryAlertVO convertToInventoryAlertVO(InventoryAlert alert) {
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

    @Override
    public RawMaterialVO moveRawMaterialLocation(String id, MoveLocationRequest request) {
        log.info("开始跨库移动 - 原材料ID: {}, 源位置: {}, 目标位置: {}, 移动数量: {}",
                id, request.getSourceLocation(), request.getTargetLocation(), request.getQuantity());

        if (!StringUtils.hasText(request.getSourceLocation()) || !StringUtils.hasText(request.getTargetLocation())) {
            throw new BusinessException("源位置和目标位置不能为空");
        }
        if (request.getSourceLocation().equals(request.getTargetLocation())) {
            throw new BusinessException("源位置和目标位置不能相同");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("移动数量必须大于0");
        }

        RawMaterial material = rawMaterialRepository.findById(id)
                .orElseThrow(() -> new BusinessException("原材料不存在"));

        if (material.getLocations() == null || material.getLocations().isEmpty()) {
            throw new BusinessException("该原材料暂无存放位置记录，无法执行跨库移动");
        }

        LocationInfo sourceLoc = material.getLocations().stream()
                .filter(l -> request.getSourceLocation().equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (sourceLoc == null) {
            throw new BusinessException("源位置不存在: " + request.getSourceLocation());
        }
        if (sourceLoc.getQuantity() < request.getQuantity()) {
            throw new BusinessException(String.format(
                    "源位置 %s 库存不足，当前可用: %d，请求移动: %d",
                    request.getSourceLocation(), sourceLoc.getQuantity(), request.getQuantity()));
        }

        sourceLoc.setQuantity(sourceLoc.getQuantity() - request.getQuantity());
        log.info("源位置 {} 减少后剩余: {}", sourceLoc.getLocation(), sourceLoc.getQuantity());

        LocationInfo targetLoc = material.getLocations().stream()
                .filter(l -> request.getTargetLocation().equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (targetLoc != null) {
            targetLoc.setQuantity(targetLoc.getQuantity() + request.getQuantity());
            log.info("目标位置 {} 已存在，增加后: {}", targetLoc.getLocation(), targetLoc.getQuantity());
        } else {
            LocationInfo newTarget = new LocationInfo();
            newTarget.setLocation(request.getTargetLocation());
            newTarget.setQuantity(request.getQuantity());
            newTarget.setCreatedAt(new Date());
            material.getLocations().add(newTarget);
            log.info("创建新目标位置 {}: {}", request.getTargetLocation(), request.getQuantity());
        }

        material.getLocations().removeIf(l -> l.getQuantity() <= 0);

        recalculateRawMaterialQuantity(material);

        RawMaterial saved = rawMaterialRepository.save(material);

        log.info("跨库移动完成 - 原材料ID: {}, 新总量: {}, 剩余位置数: {}",
                saved.getId(), saved.getQuantity(),
                saved.getLocations() != null ? saved.getLocations().size() : 0);

        return convertToRawMaterialVO(saved);
    }

    @Override
    public FinishedProductVO moveFinishedProductLocation(String id, MoveLocationRequest request) {
        log.info("开始跨库移动 - 成品ID: {}, 源位置: {}, 目标位置: {}, 移动数量: {}",
                id, request.getSourceLocation(), request.getTargetLocation(), request.getQuantity());

        if (!StringUtils.hasText(request.getSourceLocation()) || !StringUtils.hasText(request.getTargetLocation())) {
            throw new BusinessException("源位置和目标位置不能为空");
        }
        if (request.getSourceLocation().equals(request.getTargetLocation())) {
            throw new BusinessException("源位置和目标位置不能相同");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("移动数量必须大于0");
        }

        FinishedProduct product = finishedProductRepository.findById(id)
                .orElseThrow(() -> new BusinessException("成品不存在"));

        if (product.getLocations() == null || product.getLocations().isEmpty()) {
            throw new BusinessException("该成品暂无存放位置记录，无法执行跨库移动");
        }

        LocationInfo sourceLoc = product.getLocations().stream()
                .filter(l -> request.getSourceLocation().equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (sourceLoc == null) {
            throw new BusinessException("源位置不存在: " + request.getSourceLocation());
        }
        if (sourceLoc.getQuantity() < request.getQuantity()) {
            throw new BusinessException(String.format(
                    "源位置 %s 库存不足，当前可用: %d，请求移动: %d",
                    request.getSourceLocation(), sourceLoc.getQuantity(), request.getQuantity()));
        }

        sourceLoc.setQuantity(sourceLoc.getQuantity() - request.getQuantity());
        log.info("源位置 {} 减少后剩余: {}", sourceLoc.getLocation(), sourceLoc.getQuantity());

        LocationInfo targetLoc = product.getLocations().stream()
                .filter(l -> request.getTargetLocation().equals(l.getLocation()))
                .findFirst()
                .orElse(null);
        if (targetLoc != null) {
            targetLoc.setQuantity(targetLoc.getQuantity() + request.getQuantity());
            log.info("目标位置 {} 已存在，增加后: {}", targetLoc.getLocation(), targetLoc.getQuantity());
        } else {
            LocationInfo newTarget = new LocationInfo();
            newTarget.setLocation(request.getTargetLocation());
            newTarget.setQuantity(request.getQuantity());
            newTarget.setCreatedAt(new Date());
            product.getLocations().add(newTarget);
            log.info("创建新目标位置 {}: {}", request.getTargetLocation(), request.getQuantity());
        }

        product.getLocations().removeIf(l -> l.getQuantity() <= 0);

        recalculateFinishedProductQuantity(product);

        FinishedProduct saved = finishedProductRepository.save(product);

        log.info("跨库移动完成 - 成品ID: {}, 新总量: {}, 剩余位置数: {}",
                saved.getId(), saved.getQuantity(),
                saved.getLocations() != null ? saved.getLocations().size() : 0);

        return convertToFinishedProductVO(saved);
    }

    @Override
    public void fifoDeductRawMaterial(String materialId, int quantity, String reason) {
        log.info("FIFO扣减原材料 - ID: {}, 扣减数量: {}, 原因: {}", materialId, quantity, reason);

        RawMaterial material = rawMaterialRepository.findById(materialId)
                .orElseThrow(() -> new BusinessException("原材料不存在"));

        if (material.getLocations() == null || material.getLocations().isEmpty()) {
            throw new BusinessException("该原材料暂无存放位置记录，无法执行FIFO扣减");
        }

        List<LocationInfo> sortedLocations = new ArrayList<>(material.getLocations());
        sortedLocations.sort((a, b) -> {
            Date aTime = a.getCreatedAt() != null ? a.getCreatedAt() : new Date(0);
            Date bTime = b.getCreatedAt() != null ? b.getCreatedAt() : new Date(0);
            return aTime.compareTo(bTime);
        });

        int remaining = quantity;
        StringBuilder deductionLog = new StringBuilder();

        for (LocationInfo loc : sortedLocations) {
            if (remaining <= 0) break;

            int available = loc.getQuantity() != null ? loc.getQuantity() : 0;
            if (available <= 0) continue;

            int deduct = Math.min(available, remaining);
            loc.setQuantity(available - deduct);
            remaining -= deduct;

            log.info("FIFO扣减 - 位置: {}, 原库存: {}, 扣减: {}, 剩余: {}",
                    loc.getLocation(), available, deduct, loc.getQuantity());
            deductionLog.append(loc.getLocation()).append("(").append(deduct).append(")");
        }

        if (remaining > 0) {
            throw new BusinessException(String.format(
                    "FIFO扣减失败：位置总库存不足，需扣减 %d，实际可扣减 %d",
                    quantity, quantity - remaining));
        }

        material.getLocations().removeIf(l -> l.getQuantity() <= 0);

        recalculateRawMaterialQuantity(material);
        rawMaterialRepository.save(material);

        InventoryRecord record = new InventoryRecord();
        record.setInventoryType("OUT");
        record.setItemType("RAW_MATERIAL");
        record.setItemId(materialId);
        record.setItemName(material.getName());
        record.setQuantity(-quantity);
        record.setOperator("SYSTEM");
        record.setOperatorName("系统自动");
        record.setReason(reason + " [FIFO:" + deductionLog + "]");
        inventoryRecordRepository.save(record);

        if (material.getAlertThreshold() != null && material.getQuantity() <= material.getAlertThreshold()) {
            createAlertIfNeeded("RAW_MATERIAL", material.getId(), material.getName(),
                    material.getQuantity(), material.getAlertThreshold());
        }

        log.info("FIFO扣减完成 - 原材料ID: {}, 新总量: {}, 扣减详情: {}",
                material.getId(), material.getQuantity(), deductionLog);
    }

    @Override
    public void fifoDeductFinishedProduct(String finishedProductId, int quantity, String reason) {
        log.info("FIFO扣减成品 - ID: {}, 扣减数量: {}, 原因: {}", finishedProductId, quantity, reason);

        FinishedProduct product = finishedProductRepository.findById(finishedProductId)
                .orElseThrow(() -> new BusinessException("成品不存在"));

        boolean hasLocationInventory = product.getLocations() != null && !product.getLocations().isEmpty();
        StringBuilder deductionLog = new StringBuilder();
        if (!hasLocationInventory) {
            int available = product.getQuantity() != null ? product.getQuantity() : 0;
            if (available < quantity) {
                throw new BusinessException(String.format(
                        "FIFO扣减失败：成品库存不足，需扣减 %d，实际可扣减 %d",
                        quantity, available));
            }
            product.setQuantity(available - quantity);
            deductionLog.append("总库存(").append(quantity).append(")");
        } else {
            List<LocationInfo> sortedLocations = new ArrayList<>(product.getLocations());
            sortedLocations.sort((a, b) -> {
                Date aTime = a.getCreatedAt() != null ? a.getCreatedAt() : new Date(0);
                Date bTime = b.getCreatedAt() != null ? b.getCreatedAt() : new Date(0);
                return aTime.compareTo(bTime);
            });

            int remaining = quantity;
            for (LocationInfo loc : sortedLocations) {
                if (remaining <= 0) {
                    break;
                }

                int available = loc.getQuantity() != null ? loc.getQuantity() : 0;
                if (available <= 0) {
                    continue;
                }

                int deduct = Math.min(available, remaining);
                loc.setQuantity(available - deduct);
                remaining -= deduct;

                log.info("FIFO扣减成品 - 位置: {}, 原库存: {}, 扣减: {}, 剩余: {}",
                        loc.getLocation(), available, deduct, loc.getQuantity());
                deductionLog.append(loc.getLocation()).append("(").append(deduct).append(")");
            }

            if (remaining > 0) {
                throw new BusinessException(String.format(
                        "FIFO扣减失败：位置总库存不足，需扣减 %d，实际可扣减 %d",
                        quantity, quantity - remaining));
            }

            product.getLocations().removeIf(l -> l.getQuantity() == null || l.getQuantity() <= 0);
        }

        if (hasLocationInventory) {
            recalculateFinishedProductQuantity(product);
        }
        finishedProductRepository.save(product);

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

        if (product.getAlertThreshold() != null && product.getQuantity() <= product.getAlertThreshold()) {
            createAlertIfNeeded("FINISHED_PRODUCT", product.getId(), product.getName(),
                    product.getQuantity(), product.getAlertThreshold());
        }

        log.info("FIFO扣减成品完成 - 成品ID: {}, 新总量: {}, 扣减详情: {}",
                product.getId(), product.getQuantity(), deductionLog);
    }
}
