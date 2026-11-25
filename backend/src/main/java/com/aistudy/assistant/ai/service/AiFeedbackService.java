package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiFeedbackResponse;
import com.aistudy.assistant.ai.dto.CreateAiFeedbackRequest;

public interface AiFeedbackService {

    AiFeedbackResponse submitFeedback(CreateAiFeedbackRequest request);
}
