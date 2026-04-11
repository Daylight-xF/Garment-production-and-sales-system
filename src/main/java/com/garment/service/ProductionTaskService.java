package com.garment.service;

import com.garment.dto.TaskCreateRequest;
import com.garment.dto.TaskUpdateRequest;
import com.garment.dto.TaskVO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductionTaskService {

    TaskVO createTask(TaskCreateRequest request, String userId);

    Page<TaskVO> getTaskList(String planId, String assignee, String status, Pageable pageable);

    TaskVO getTaskById(String id);

    TaskVO updateTask(String id, TaskUpdateRequest request);

    TaskVO assignTask(String id, String assignee);

    TaskVO updateProgress(String id, Integer progress);
}
