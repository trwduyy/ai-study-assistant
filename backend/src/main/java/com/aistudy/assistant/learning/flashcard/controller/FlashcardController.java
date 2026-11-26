package com.aistudy.assistant.learning.flashcard.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardDeckRequest;
import com.aistudy.assistant.learning.flashcard.dto.CreateFlashcardRequest;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckDetailResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardDeckSummaryResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardItemResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewQueueResponse;
import com.aistudy.assistant.learning.flashcard.dto.FlashcardReviewResultResponse;
import com.aistudy.assistant.learning.flashcard.dto.SubmitFlashcardReviewRequest;
import com.aistudy.assistant.learning.flashcard.dto.UpdateFlashcardDeckRequest;
import com.aistudy.assistant.learning.flashcard.service.FlashcardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for Flashcard decks, cards, and SM-2 spaced repetition review (FR-08).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Flashcards & SM-2 V2", description = "Quản lý bộ thẻ flashcard và ôn tập lặp lại ngắt quãng SM-2")
@SecurityRequirement(name = "BearerAuth")
public class FlashcardController {

    private final FlashcardService flashcardService;

    @GetMapping("/flashcard-decks")
    @Operation(summary = "Danh sách bộ flashcard của người dùng kèm số thẻ đến hạn ôn")
    public ResponseEntity<ApiResponse<List<FlashcardDeckSummaryResponse>>> listDecks(
            @RequestParam(value = "courseId", required = false) Long courseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FlashcardDeckSummaryResponse> page = flashcardService.listDecks(courseId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping("/flashcard-decks")
    @Operation(summary = "Tạo bộ flashcard mới")
    public ResponseEntity<ApiResponse<FlashcardDeckSummaryResponse>> createDeck(
            @Valid @RequestBody CreateFlashcardDeckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(flashcardService.createDeck(request)));
    }

    @GetMapping("/flashcard-decks/{id}")
    @Operation(summary = "Xem chi tiết bộ thẻ và danh sách thẻ")
    public ResponseEntity<ApiResponse<FlashcardDeckDetailResponse>> getDeckDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(flashcardService.getDeckDetail(id)));
    }

    @PutMapping("/flashcard-decks/{id}")
    @Operation(summary = "Cập nhật thông tin bộ thẻ")
    public ResponseEntity<ApiResponse<FlashcardDeckSummaryResponse>> updateDeck(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFlashcardDeckRequest request) {
        return ResponseEntity.ok(ApiResponse.success(flashcardService.updateDeck(id, request)));
    }

    @DeleteMapping("/flashcard-decks/{id}")
    @Operation(summary = "Xóa bộ flashcard")
    public ResponseEntity<ApiResponse<Void>> deleteDeck(@PathVariable Long id) {
        flashcardService.deleteDeck(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/flashcard-decks/{id}/cards")
    @Operation(summary = "Thêm flashcard thủ công vào bộ thẻ")
    public ResponseEntity<ApiResponse<FlashcardItemResponse>> addCard(
            @PathVariable Long id,
            @Valid @RequestBody CreateFlashcardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(flashcardService.addCard(id, request)));
    }

    @GetMapping("/flashcard-decks/{id}/review")
    @Operation(summary = "Lấy danh sách thẻ đến hạn ôn hôm nay (chưa ôn hoặc nextReviewAt <= now)")
    public ResponseEntity<ApiResponse<FlashcardReviewQueueResponse>> getDueCards(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(flashcardService.getDueCards(id)));
    }

    @PostMapping("/flashcards/{id}/reviews")
    @Operation(summary = "Chấm mức độ ghi nhớ (FORGOT, HARD, OK, EASY) theo thuật toán SM-2")
    public ResponseEntity<ApiResponse<FlashcardReviewResultResponse>> submitReview(
            @PathVariable Long id,
            @Valid @RequestBody SubmitFlashcardReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(flashcardService.submitReview(id, request)));
    }
}
