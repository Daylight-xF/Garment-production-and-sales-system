package com.garment.service;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.dto.TaskVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductionPlanService {

    PlanVO createPlan(PlanCreateRequest request, String userId);

    Page<PlanVO> getPlanList(String keyword, String status, Pageable pageable);

    PlanVO getPlanById(String id);

    PlanVO updatePlan(String id, PlanUpdateRequest request);

    void deletePlan(String id);

    PlanVO approvePlan(String id, String status);

    PlanVO startProduction(String planId, String userId);

    PlanVO completePlan(String planId);

    List<TaskVO> getTasksByPlanId(String planId);
}
