// =============================================================================
// Common API Response Types
// =============================================================================

export interface ApiResponse<T> {
    success: boolean;
    message: string;
    data: T;
    timestamp: string;
}

// =============================================================================
// Auth & User Types
// =============================================================================

export interface User {
    id: number;
    username: string;
    email: string;
    fullName: string;
    createdAt?: string;
    updatedAt?: string;
}

export interface RegisterRequest {
    username: string;
    email: string;
    password: string;
    fullName: string;
}

export interface LoginRequest {
    username: string;
    password: string;
}

export interface AuthResponse {
    token: string;
    tokenType: string;
    expiresIn: number;
    user: User;
}

export interface ChangePasswordRequest {
    oldPassword: string;
    newPassword: string;
}

export interface UpdateProfileRequest {
    fullName: string;
}

// =============================================================================
// Course & Topic Types
// =============================================================================

export interface Topic {
    id: number;
    courseId?: number;
    title: string;
    orderIndex: number;
    createdAt?: string;
}

export interface CreateTopicRequest {
    title: string;
    orderIndex?: number;
}

export interface UpdateTopicRequest {
    title: string;
    orderIndex?: number;
}

export interface Course {
    id: number;
    title: string;
    description: string | null;
    topicCount: number;
    materialCount: number;
    createdAt: string;
    updatedAt: string;
}

export interface CourseDetail {
    id: number;
    title: string;
    description: string | null;
    createdAt: string;
    updatedAt: string;
    topics: Topic[];
}

export interface CreateCourseRequest {
    title: string;
    description?: string;
}

export interface UpdateCourseRequest {
    title: string;
    description?: string;
}

// =============================================================================
// Material Types
// =============================================================================

export type ExtractionStatus = "PENDING" | "SUCCESS" | "FAILED";

export interface Material {
    id: number;
    courseId: number;
    topicId: number | null;
    originalName: string;
    fileType: string;
    fileSize: number;
    extractionStatus: ExtractionStatus;
    createdAt: string;
}

export interface MaterialDetail {
    id: number;
    courseId: number;
    topicId: number | null;
    originalName: string;
    fileName: string;
    fileType: string;
    fileSize: number;
    extractedText: string | null;
    extractionStatus: ExtractionStatus;
    errorMessage: string | null;
    createdAt: string;
}

export interface MaterialUploadResponse {
    id: number;
    courseId: number;
    topicId: number | null;
    fileName: string;
    originalName: string;
    fileType: string;
    fileSize: number;
    extractionStatus: ExtractionStatus;
    extractedTextLength: number;
    createdAt: string;
}

// =============================================================================
// Summary Types
// =============================================================================

export type LengthType = "SHORT" | "MEDIUM" | "DETAILED";

export interface Summary {
    id: number;
    materialId: number;
    lengthType: LengthType;
    content: string;
    createdAt: string;
}

export interface GenerateSummaryRequest {
    materialId: number;
    lengthType: LengthType;
}

// =============================================================================
// Chat Types
// =============================================================================

export type ChatRole = "USER" | "ASSISTANT";

export interface ChatMessage {
    id: number;
    role: ChatRole;
    content: string;
    createdAt: string;
}

export interface ChatSession {
    id: number;
    title: string;
    courseId: number | null;
    materialId: number | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateChatSessionRequest {
    title: string;
    courseId?: number;
    materialId?: number;
}

export interface SendMessageRequest {
    content: string;
}

export interface ChatAnswerResponse {
    userMessage: ChatMessage;
    assistantMessage: ChatMessage;
}

// =============================================================================
// Quiz Types
// =============================================================================

export type Difficulty = "EASY" | "MEDIUM" | "HARD";
export type OptionLetter = "A" | "B" | "C" | "D";

export interface Quiz {
    id: number;
    title: string;
    difficulty: Difficulty;
    questionCount: number;
    courseId: number;
    materialId: number | null;
    createdAt: string;
}

export interface GenerateQuizRequest {
    courseId: number;
    materialId?: number;
    title: string;
    difficulty: Difficulty;
    questionCount: number;
}

export interface QuizQuestionExam {
    id: number;
    orderIndex: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
}

export interface QuizExam {
    id: number;
    title: string;
    difficulty: Difficulty;
    questionCount: number;
    questions: QuizQuestionExam[];
}

export interface QuestionAnswerSubmit {
    questionId: number;
    selectedOption?: OptionLetter;
}

export interface SubmitQuizRequest {
    answers: QuestionAnswerSubmit[];
}

export interface QuizSubmitResult {
    attemptId: number;
    quizId: number;
    score: number;
    totalQuestions: number;
    correctCount: number;
    submittedAt: string;
}

export interface AttemptAnswerDetail {
    questionId: number;
    questionText: string;
    optionA: string;
    optionB: string;
    optionC: string;
    optionD: string;
    selectedOption: OptionLetter | null;
    correctOption: OptionLetter;
    isCorrect: boolean;
    explanation: string;
}

export interface AttemptDetail {
    attemptId: number;
    quizId: number;
    quizTitle: string;
    score: number;
    totalQuestions: number;
    correctCount: number;
    submittedAt: string;
    answers: AttemptAnswerDetail[];
}

export interface QuizAttemptHistory {
    attemptId: number;
    score: number;
    correctCount: number;
    totalQuestions: number;
    submittedAt: string;
}

// =============================================================================
// Learning Analytics Types
// =============================================================================

export interface AnalyticsOverview {
    totalCourses: number;
    totalMaterials: number;
    totalQuizzesTaken: number;
    averageScore: number;
    totalQuestionsAttempted: number;
    totalCorrectAnswers: number;
    accuracyRate: number;
}

export interface ScoreTrendItem {
    quizId: number;
    quizTitle: string;
    score: number;
    date: string;
}

export interface CourseAnalytics {
    courseId: number;
    courseTitle: string;
    materialCount: number;
    quizzesCount: number;
    quizzesAttempted: number;
    averageScore: number | null;
    latestScore: number | null;
    scoreTrend: ScoreTrendItem[];
}

export interface ReviewRecommendation {
    courseId: number;
    courseTitle: string;
    recommendedTopic: string;
    reason: string;
    sampleQuestion: string;
    suggestedAction: string;
}
