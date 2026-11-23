package com.aistudy.assistant.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerResponse {

    private ChatMessageResponse userMessage;
    private ChatMessageResponse assistantMessage;
}
