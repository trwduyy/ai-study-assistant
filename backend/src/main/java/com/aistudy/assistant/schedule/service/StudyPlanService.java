package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.DecideStudyPlanItemRequest;
import com.aistudy.assistant.schedule.dto.StudyPlanItemResponse;
import com.aistudy.assistant.schedule.dto.StudyPlanResponse;

public interface StudyPlanService {

    StudyPlanResponse getStudyPlan(Long planId);

    StudyPlanItemResponse decidePlanItem(Long planId, Long itemId, DecideStudyPlanItemRequest request);
}
