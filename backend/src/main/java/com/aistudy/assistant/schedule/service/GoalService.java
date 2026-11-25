package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.CreateGoalRequest;
import com.aistudy.assistant.schedule.dto.GoalResponse;
import com.aistudy.assistant.schedule.dto.UpdateGoalRequest;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GoalService {

    Page<GoalResponse> listGoals(GoalStatus status, Pageable pageable);

    GoalResponse createGoal(CreateGoalRequest request);

    GoalResponse updateGoal(Long goalId, UpdateGoalRequest request);

    void deleteGoal(Long goalId);
}
