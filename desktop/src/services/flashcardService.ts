import apiClient from "./apiClient";
import {
    ApiResponse,
    CreateFlashcardDeckRequest,
    CreateFlashcardRequest,
    FlashcardDeckDetail,
    FlashcardDeckSummary,
    FlashcardItem,
    FlashcardReviewQueue,
    FlashcardReviewResult,
    GenerateAiFlashcardsRequest,
    PageMeta,
    SubmitFlashcardReviewRequest,
    UpdateFlashcardDeckRequest
} from "../types";
import aiService from "./aiService";

export interface PaginatedFlashcardDecks {
    items: FlashcardDeckSummary[];
    meta?: PageMeta | null;
}

export const flashcardService = {
    async listDecks(courseId?: number, page = 0, size = 20): Promise<PaginatedFlashcardDecks> {
        const params: Record<string, number> = { page, size };
        if (courseId) params.courseId = courseId;
        const response = await apiClient.get<ApiResponse<FlashcardDeckSummary[]>>("/flashcard-decks", {
            params
        });
        return {
            items: response.data.data,
            meta: response.data.meta
        };
    },

    async createDeck(data: CreateFlashcardDeckRequest): Promise<FlashcardDeckSummary> {
        const response = await apiClient.post<ApiResponse<FlashcardDeckSummary>>("/flashcard-decks", data);
        return response.data.data;
    },

    async getDeckDetail(id: number): Promise<FlashcardDeckDetail> {
        const response = await apiClient.get<ApiResponse<FlashcardDeckDetail>>(`/flashcard-decks/${id}`);
        return response.data.data;
    },

    async updateDeck(id: number, data: UpdateFlashcardDeckRequest): Promise<FlashcardDeckSummary> {
        const response = await apiClient.put<ApiResponse<FlashcardDeckSummary>>(`/flashcard-decks/${id}`, data);
        return response.data.data;
    },

    async deleteDeck(id: number): Promise<void> {
        await apiClient.delete<ApiResponse<null>>(`/flashcard-decks/${id}`);
    },

    async addCard(deckId: number, data: CreateFlashcardRequest): Promise<FlashcardItem> {
        const response = await apiClient.post<ApiResponse<FlashcardItem>>(
            `/flashcard-decks/${deckId}/cards`,
            data
        );
        return response.data.data;
    },

    async getDueCards(deckId: number): Promise<FlashcardReviewQueue> {
        const response = await apiClient.get<ApiResponse<FlashcardReviewQueue>>(`/flashcard-decks/${deckId}/review`);
        return response.data.data;
    },

    async submitReview(cardId: number, data: SubmitFlashcardReviewRequest): Promise<FlashcardReviewResult> {
        const response = await apiClient.post<ApiResponse<FlashcardReviewResult>>(
            `/flashcards/${cardId}/reviews`,
            data
        );
        return response.data.data;
    },

    async generateAiFlashcards(data: GenerateAiFlashcardsRequest) {
        return aiService.requestFlashcards(data);
    }
};

export default flashcardService;
