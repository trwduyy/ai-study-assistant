export * from "./api";
export * from "./auth";
export * from "./realtime";
export * from "./classroom";
export * from "./schedule";

// =============================================================================
// Learning & Curriculum V2 Types (Course, Chapter, Topic, Material)
// =============================================================================

export interface Topic {
    id: number;
    chapterId?: number;
    title: string;
    position: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface CreateTopicRequest {
    title: string;
    position?: number;
}

export interface UpdateTopicRequest {
    title: string;
    position?: number;
}

export interface Chapter {
    id: number;
    courseId: number;
    title: string;
    position: number;
    topics?: Topic[];
    createdAt?: string;
    updatedAt?: string;
}

export interface CreateChapterRequest {
    title: string;
    position?: number;
}

export interface UpdateChapterRequest {
    title: string;
    position?: number;
}

export interface Course {
    id: number;
    name: string;
    description: string | null;
    chapterCount?: number;
    materialCount?: number;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface CourseDetail {
    id: number;
    name: string;
    description: string | null;
    version: number;
    chapters: Chapter[];
    createdAt: string;
    updatedAt: string;
}

export interface CreateCourseRequest {
    name: string;
    description?: string;
}

export interface UpdateCourseRequest {
    name: string;
    description?: string;
    version: number;
}

export type MaterialCategory =
    | "SLIDE"
    | "LECTURE_NOTE"
    | "TEXTBOOK"
    | "EXERCISE"
    | "OTHER";

export type MaterialStatus = "EXTRACTING" | "EXTRACTED" | "FAILED";

export interface Material {
    id: number;
    courseId: number;
    topicId: number | null;
    fileName: string;
    mimeType: string;
    sizeBytes: number;
    category: MaterialCategory;
    status: MaterialStatus;
    pageCount: number | null;
    createdAt: string;
    updatedAt: string;
}

export interface MaterialDetail {
    id: number;
    courseId: number;
    topicId: number | null;
    fileName: string;
    mimeType: string;
    sizeBytes: number;
    category: MaterialCategory;
    status: MaterialStatus;
    pageCount: number | null;
    chunkCount: number | null;
    contentHash: string;
    errorCode: string | null;
    errorMessage: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface MaterialChunk {
    chunkId: number;
    chunkIndex: number;
    pageFrom: number;
    pageTo: number;
    content: string;
    tokenCount: number;
}

export interface UpdateMaterialRequest {
    fileName?: string;
    topicId?: number | null;
    category?: MaterialCategory;
}

export interface ReextractResponse {
    id: number;
    status: MaterialStatus;
    updatedAt: string;
}

// =============================================================================
// AI V2 Types (Jobs, Summaries, Explanations, Grounded Chat & Feedback)
// =============================================================================

export interface AiJobAcceptedResponse {
    jobId: string;
    status: "PENDING";
}

export interface AiJobCancelResponse {
    jobId: string;
    status: "CANCELLED";
}

export type SummaryLength = "SHORT" | "MEDIUM" | "DETAILED";

export interface CreateSummaryRequest {
    materialId: number;
    length: SummaryLength;
}

export interface AiSummary {
    id: number;
    materialId: number;
    length: SummaryLength;
    content: string;
    model: string;
    promptVersion: string;
    createdAt: string;
    updatedAt: string;
}

export interface CreateExplanationRequest {
    materialId: number;
    concept: string;
    contextSnippet?: string;
    allowExternalKnowledge?: boolean;
}

export interface AiSourceCitation {
    materialId: number;
    fileName: string;
    page: number;
    chunkId: number;
}

export interface CreateAiChatRequest {
    title: string;
    courseId: number;
}

export interface AiChatSession {
    id: number;
    courseId: number;
    title: string;
    createdAt: string;
    updatedAt: string;
}

export interface AiChatMessage {
    id: number;
    role: "USER" | "ASSISTANT";
    content: string;
    grounded: boolean;
    sources: AiSourceCitation[];
    aiAddedNote: string | null;
    model?: string;
    promptVersion?: string;
    createdAt: string;
}

export interface AiChatDetail {
    id: number;
    courseId: number;
    title: string;
    messages: AiChatMessage[];
}

export interface SendAiChatMessageRequest {
    content: string;
    materialIds: number[];
    allowExternalKnowledge?: boolean;
}

export interface AiChatAnswerResponse {
    messageId: number;
    answer: string;
    grounded: boolean;
    sources: AiSourceCitation[];
    aiAddedNote: string | null;
    model: string;
    promptVersion: string;
    createdAt: string;
}

export interface CreateAiFeedbackRequest {
    targetType: "AI_CHAT_MESSAGE";
    targetId: number;
    reason: string;
}

export interface AiFeedbackResponse {
    id: number;
    targetType: string;
    targetId: number;
    reason: string;
    status: "OPEN" | "REVIEWED" | "RESOLVED";
    createdAt: string;
}

// =============================================================================
// Quiz V2 Types (Generate, Question, Attempt, Idempotent Submit, Review)
// =============================================================================

export type QuizDifficulty = "EASY" | "MEDIUM" | "HARD";

export interface GenerateAiQuizRequest {
    courseId: number;
    materialIds: number[];
    topicId?: number;
    title: string;
    topic?: string;
    questionCount: number;
    difficulty: QuizDifficulty;
    type?: string;
}

export interface QuizSummary {
    id: number;
    courseId: number;
    title: string;
    difficulty: QuizDifficulty;
    questionCount: number;
    model?: string;
    promptVersion?: string;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface QuizOption {
    id: number;
    label: string;
    content: string;
    correct?: boolean;
}

export interface QuizQuestion {
    id: number;
    topicId?: number | null;
    position: number;
    content: string;
    type: string;
    options: QuizOption[];
}

export interface QuizDetail {
    id: number;
    courseId: number;
    title: string;
    difficulty: QuizDifficulty;
    questionCount: number;
    version: number;
    questions: QuizQuestion[];
}

export interface UpdateQuizRequest {
    title: string;
    difficulty?: QuizDifficulty;
    version: number;
}

export interface StartAttemptResponse {
    attemptId: number;
    quizId: number;
    status: "IN_PROGRESS" | "SUBMITTED" | "EXPIRED";
    startedAt: string;
    deadlineAt: string | null;
    maxScore: number;
    version: number;
    answers: Array<{ questionId: number; selectedOptionIds: number[] }>;
}

export interface SaveAttemptDraftRequest {
    version: number;
    answers: Array<{ questionId: number; selectedOptionIds: number[] }>;
}

export interface SaveAttemptDraftResponse {
    attemptId: number;
    status: string;
    version: number;
    updatedAt: string;
}

export interface SubmitAttemptRequest {
    version: number;
    durationSeconds?: number;
    answers: Array<{ questionId: number; selectedOptionIds: number[] }>;
}

export interface QuizSubmitDetail {
    questionId: number;
    selectedOptionIds: number[];
    correctOptionIds: number[];
    correct: boolean;
    explanation: string;
}

export interface QuizSubmitResult {
    attemptId: number;
    quizId: number;
    status: string;
    score: number;
    maxScore: number;
    correctCount: number;
    totalQuestions: number;
    durationSeconds: number;
    submittedAt: string;
    details: QuizSubmitDetail[];
}

export interface QuizAttemptSummary {
    attemptId: number;
    status: string;
    score: number;
    maxScore: number;
    correctCount: number;
    totalQuestions: number;
    durationSeconds: number;
    startedAt: string;
    submittedAt: string;
}

export interface AttemptReviewQuestion {
    questionId: number;
    position: number;
    content: string;
    type: string;
    options: QuizOption[];
    selectedOptionIds: number[];
    correct: boolean;
    explanation: string;
    sourceChunkId?: number | null;
}

export interface AttemptReview {
    attemptId: number;
    quizId: number;
    quizTitle: string;
    status: string;
    score: number;
    maxScore: number;
    correctCount: number;
    totalQuestions: number;
    durationSeconds: number;
    submittedAt: string;
    questions: AttemptReviewQuestion[];
}

// =============================================================================
// Flashcards & SM-2 Spaced Repetition Types (FR-08)
// =============================================================================

export type FlashcardRating = "FORGOT" | "HARD" | "OK" | "EASY";

export interface GenerateAiFlashcardsRequest {
    materialIds: number[];
    deckId?: number | null;
    deckTitle: string;
    courseId: number;
    cardCount: number;
}

export interface FlashcardDeckSummary {
    id: number;
    courseId: number;
    courseName?: string;
    title: string;
    totalCards: number;
    dueCardsCount: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateFlashcardDeckRequest {
    courseId: number;
    title: string;
}

export interface UpdateFlashcardDeckRequest {
    courseId: number;
    title: string;
}

export interface FlashcardItem {
    id: number;
    deckId: number;
    front: string;
    back: string;
    sourceChunkId?: number | null;
    lastRating?: FlashcardRating | null;
    easeFactor: number;
    intervalDays: number;
    repetitions: number;
    nextReviewAt?: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateFlashcardRequest {
    front: string;
    back: string;
    sourceChunkId?: number | null;
}

export interface FlashcardDeckDetail {
    id: number;
    courseId: number;
    courseName?: string;
    title: string;
    totalCards: number;
    dueCardsCount: number;
    cards: FlashcardItem[];
    createdAt: string;
    updatedAt: string;
}

export interface FlashcardReviewQueue {
    deckId: number;
    deckTitle: string;
    dueCount: number;
    cards: FlashcardItem[];
}

export interface SubmitFlashcardReviewRequest {
    rating: FlashcardRating;
}

export interface FlashcardReviewResult {
    reviewId: number;
    cardId: number;
    rating: FlashcardRating;
    easeFactor: number;
    intervalDays: number;
    repetitions: number;
    nextReviewAt: string;
    reviewedAt: string;
}

// =============================================================================
// Learning Analytics & Student Dashboard Types
// =============================================================================

export interface AnalyticsOverview {
    totalCourses: number;
    totalMaterials: number;
    totalAttempts: number;
    averageScore: number;
    maxScoreScale: number;
    totalQuestionsAttempted: number;
    totalCorrectAnswers: number;
    accuracyRate: number;
}

export interface ScoreTrendItem {
    attemptId: number;
    quizId: number;
    quizTitle: string;
    courseId: number;
    score: number;
    maxScore: number;
    submittedAt: string;
}

export interface FrequentMistake {
    questionId: number;
    courseId: number;
    courseName: string;
    topicId: number;
    topicTitle: string;
    questionContent: string;
    totalAttempts: number;
    wrongCount: number;
    accuracyRate: number;
}

export type ReviewConfidence = "HIGH" | "MEDIUM" | "LOW";

export interface ReviewTopic {
    courseId: number;
    courseName: string;
    topicId: number;
    topicTitle: string;
    accuracyRate: number;
    sampleSize: number;
    confidence: ReviewConfidence;
    reason: string;
    suggestedMaterialId?: number | null;
    suggestedAction?: string | null;
}

export interface StudentDashboardData {
    overview: {
        totalCourses: number;
        totalMaterials: number;
        averageScore: number;
        accuracyRate: number;
    };
    recentAttempts: Array<{
        attemptId: number;
        quizId: number;
        quizTitle: string;
        score: number;
        maxScore: number;
        submittedAt: string;
    }>;
    reviewTopics: Array<{
        topicId: number;
        topicTitle: string;
        confidence: string;
        reason: string;
    }>;
    upcomingEvents: Array<{
        eventId?: number;
        id?: number;
        classId?: number | null;
        className?: string | null;
        type?: string;
        title: string;
        startAt?: string;
        endAt?: string;
        startTime?: string;
        endTime?: string;
        timezone?: string;
        status?: string;
        meetingUrl?: string | null;
        source?: string;
    }>;
    dueAssignments: Array<{
        assignmentId?: number;
        id?: number;
        classId?: number;
        className?: string;
        title: string;
        dueAt?: string;
        deadline?: string;
        courseName?: string;
        allowLate?: boolean;
        maxScore?: number;
    }>;
    unreadNotificationCount: number;
    todayTasks: Array<{
        taskId?: number;
        id?: number;
        goalId?: number | null;
        title: string;
        taskDate?: string;
        priority?: string;
        status?: string;
        completed?: boolean;
        remindAt?: string | null;
    }>;
    dueFlashcardsCount?: number;
}
