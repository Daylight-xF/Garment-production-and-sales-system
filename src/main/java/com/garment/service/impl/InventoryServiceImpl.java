package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.*;
import com.garment.repository.*;
import com.garment.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final RawMaterialRepository rawMaterialRepository;
    private final FinishedProductRepository finishedProductRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryAlertRepository inventoryAlertRepository;
    private final UserRepository userRepository;

    public InventoryServiceImpl(RawMaterialRepository rawMaterialRepository,
                                 FinishedProductRepository finishedProductRepository,
                                 InventoryRecordRepository inventoryRecordRepository,
                                 InventoryAlertRepository inventoryAlertRepository,
                                 UserRepository userRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.finishedProductRepository = finishedProductRepository;
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
        material.setLocation(request.getLocation());
        material.setSupplier(request.getSupplier());
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
        if (request.getLocation() != null) {
            material.setLocation(request.getLocation());
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
    public Page<FinishedProductVO> getFinishedProductList(String name, String category, Pageable pageable) {
        List<FinishedProduct> all = finishedProductRepository.findAll();
        List<FinishedProduct> filtered = all.stream()
                .filter(p -> !StringUtils.hasText(name) || (p.getName() != null && p.getName().contains(name)))
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
        product.setSpecification(request.getSpecification());
        product.setUnit(request.getUnit());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : 0);
        product.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 0);
        product.setLocation(request.getLocation());
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
        if (request.getSpecification() != null) {
            product.setSpecification(request.getSpecification());
        }
        if (request.getUnit() != null) {
            product.setUnit(request.getUnit());
        }
        if (request.getLocation() != null) {
            product.setLocation(request.getLocation());
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
        String itemName = "";
        if ("RAW_MATERIAL".equals(request.getItemType())) {
            RawMaterial material = rawMaterialRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("原材料不存在"));
            material.setQuantity(material.getQuantity() + request.getQuantity());
            rawMaterialRepository.save(material);
            itemName = material.getName();
        } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
            FinishedProduct product = finishedProductRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("成品不存在"));
            product.setQuantity(product.getQuantity() + request.getQuantity());
            finishedProductRepository.save(product);
            itemName = product.getName();
        } else {
            throw new BusinessException("无效的物品类型");
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
        return convertToInventoryRecordVO(saved);
    }

    @Override
    public InventoryRecordVO stockOut(StockInOutRequest request, String operatorId) {
        String itemName = "";
        if ("RAW_MATERIAL".equals(request.getItemType())) {
            RawMaterial material = rawMaterialRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("原材料不存在"));
            if (material.getQuantity() < request.getQuantity()) {
                throw new BusinessException("库存不足，当前库存：" + material.getQuantity());
            }
            material.setQuantity(material.getQuantity() - request.getQuantity());
            rawMaterialRepository.save(material);
            itemName = material.getName();

            if (material.getAlertThreshold() != null && material.getQuantity() <= material.getAlertThreshold()) {
                createAlertIfNeeded("RAW_MATERIAL", material.getId(), material.getName(),
                        material.getQuantity(), material.getAlertThreshold());
            }
        } else if ("FINISHED_PRODUCT".equals(request.getItemType())) {
            FinishedProduct product = finishedProductRepository.findById(request.getItemId())
                    .orElseThrow(() -> new BusinessException("成品不存在"));
            if (product.getQuantity() < request.getQuantity()) {
                throw new BusinessException("库存不足，当前库存：" + product.getQuantity());
            }
            product.setQuantity(product.getQuantity() - request.getQuantity());
            finishedProductRepository.save(product);
            itemName = product.getName();

            if (product.getAlertThreshold() != null && product.getQuantity() <= product.getAlertThreshold()) {
                createAlertIfNeeded("FINISHED_PRODUCT", product.getId(), product.getName(),
                        product.getQuantity(), product.getAlertThreshold());
            }
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

    private RawMaterialVO convertToRawMaterialVO(RawMaterial material) {
        return RawMaterialVO.builder()
                .id(material.getId())
                .name(material.getName())
                .category(material.getCategory())
                .specification(material.getSpecification())
                .unit(material.getUnit())
                .quantity(material.getQuantity())
                .alertThreshold(material.getAlertThreshold())
                .location(material.getLocation())
                .supplier(material.getSupplier())
                .price(material.getPrice())
                .description(material.getDescription())
                .createTime(material.getCreateTime())
                .updateTime(material.getUpdateTime())
                .build();
    }

    private FinishedProductVO convertToFinishedProductVO(FinishedProduct product) {
        return FinishedProductVO.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .specification(product.getSpecification())
                .unit(product.getUnit())
                .quantity(product.getQuantity())
                .alertThreshold(product.getAlertThreshold())
                .location(product.getLocation())
                .price(product.getPrice())
                .costPrice(product.getCostPrice())
                .description(product.getDescription())
                .createTime(product.getCreateTime())
                .updateTime(product.getUpdateTime())
                .build();
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
}
