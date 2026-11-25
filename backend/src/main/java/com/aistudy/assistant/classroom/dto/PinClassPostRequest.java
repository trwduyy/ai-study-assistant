package com.aistudy.assistant.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for setting or toggling pinned state of a classroom post.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinClassPostRequest {

    private Boolean pinned;
}
