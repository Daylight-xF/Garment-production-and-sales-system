package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.exception.BusinessException;
import com.garment.model.ProductionPlan;
import com.garment.model.User;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.UserRepository;
import com.garment.service.ProductionPlanService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private final ProductionPlanRepository productionPlanRepository;
    private final UserRepository userRepository;

    public ProductionPlanServiceImpl(ProductionPlanRepository productionPlanRepository,
                                      UserRepository userRepository) {
        this.productionPlanRepository = productionPlanRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PlanVO createPlan(PlanCreateRequest request, String userId) {
        ProductionPlan plan = new ProductionPlan();
        plan.setPlanName(request.getPlanName());
        plan.setProductName(request.getProductName());
        plan.setQuantity(request.getQuantity());
        plan.setCompletedQuantity(0);
        plan.setUnit(request.getUnit());
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus("PENDING");
        plan.setDescription(request.getDescription());
        plan.setCreateBy(userId);

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    @Override
    public Page<PlanVO> getPlanList(String keyword, String status, Pageable pageable) {
        List<ProductionPlan> allPlans = productionPlanRepository.findAll();

        List<ProductionPlan> filtered = allPlans.stream()
                .filter(plan -> {
                    boolean matchKeyword = true;
                    if (StringUtils.hasText(keyword)) {
                        matchKeyword = (plan.getPlanName() != null && plan.getPlanName().contains(keyword))
                                || (plan.getProductName() != null && plan.getProductName().contains(keyword));
                    }
                    boolean matchStatus = true;
                    if (StringUtils.hasText(status)) {
                        matchStatus = status.equals(plan.getStatus());
                    }
                    return matchKeyword && matchStatus;
                })
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ProductionPlan> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<PlanVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public PlanVO getPlanById(String id) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));
        return convertToVO(plan);
    }

    @Override
    public PlanVO updatePlan(String id, PlanUpdateRequest request) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (request.getPlanName() != null) {
            plan.setPlanName(request.getPlanName());
        }
        if (request.getProductName() != null) {
            plan.setProductName(request.getProductName());
        }
        if (request.getQuantity() != null) {
            plan.setQuantity(request.getQuantity());
        }
        if (request.getUnit() != null) {
            plan.setUnit(request.getUnit());
        }
        if (request.getStartDate() != null) {
            plan.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            plan.setEndDate(request.getEndDate());
        }
        if (request.getDescription() != null) {
            plan.setDescription(request.getDescription());
        }

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    @Override
    public void deletePlan(String id) {
        if (!productionPlanRepository.existsById(id)) {
            throw new BusinessException("生产计划不存在");
        }
        productionPlanRepository.deleteById(id);
    }

    @Override
    public PlanVO approvePlan(String id, String status) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (!"PENDING".equals(plan.getStatus())) {
            throw new BusinessException("只有待审批状态的计划才能审批");
        }

        if (!"APPROVED".equals(status) && !"CANCELLED".equals(status)) {
            throw new BusinessException("审批状态只能为APPROVED或CANCELLED");
        }

        plan.setStatus(status);
        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    private PlanVO convertToVO(ProductionPlan plan) {
        String createByName = null;
        if (StringUtils.hasText(plan.getCreateBy())) {
            createByName = userRepository.findById(plan.getCreateBy())
                    .map(User::getRealName)
                    .orElse(null);
        }

        return PlanVO.builder()
                .id(plan.getId())
                .planName(plan.getPlanName())
                .productName(plan.getProductName())
                .quantity(plan.getQuantity())
                .completedQuantity(plan.getCompletedQuantity())
                .unit(plan.getUnit())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .description(plan.getDescription())
                .createBy(plan.getCreateBy())
                .createByName(createByName)
                .createTime(plan.getCreateTime())
                .updateTime(plan.getUpdateTime())
                .build();
    }
}
