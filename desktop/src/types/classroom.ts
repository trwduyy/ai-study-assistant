export type ClassRole = "OWNER_TEACHER" | "CO_TEACHER" | "STUDENT";
export type ClassroomStatus = "OPEN" | "CLOSED" | "ARCHIVED";
export type ClassMemberStatus = "ACTIVE" | "LEFT" | "REMOVED";
export type JoinRequestStatus = "PENDING" | "APPROVED" | "REJECTED";
export type SubmissionStatus = "SUBMITTED" | "GRADED" | "RETURNED" | "REOPENED";

// =============================================================================
// Classroom Interfaces
// =============================================================================

export interface Classroom {
    id: number;
    code: string;
    name: string;
    description: string | null;
    subject: string;
    avatarUrl: string | null;
    startAt: string | null;
    endAt: string | null;
    status: ClassroomStatus;
    ownerId: number;
    ownerName: string;
    myClassRole: ClassRole;
    memberCount: number;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface CreateClassroomRequest {
    name: string;
    description?: string;
    subject: string;
    avatarUrl?: string;
    startAt?: string;
    endAt?: string;
}

export interface UpdateClassroomRequest {
    name: string;
    description?: string;
    subject: string;
    avatarUrl?: string;
    startAt?: string;
    endAt?: string;
    version?: number;
}

export interface UpdateClassroomStatusRequest {
    status: ClassroomStatus;
    version?: number;
}

// =============================================================================
// Class Members & Join Requests
// =============================================================================

export interface ClassMember {
    id: number;
    classId: number;
    userId: number;
    fullName: string;
    email: string;
    avatarUrl: string | null;
    classRole: ClassRole;
    status: ClassMemberStatus;
    joinedAt: string;
}

export interface AddCoTeacherRequest {
    userId: number;
}

export interface ClassJoinRequest {
    id: number;
    classId: number;
    className: string;
    studentId: number;
    studentName: string;
    studentEmail: string;
    status: JoinRequestStatus;
    decidedById: number | null;
    decidedByName: string | null;
    decidedAt: string | null;
    rejectionReason: string | null;
    createdAt: string;
    updatedAt: string;
}

export interface CreateJoinRequestRequest {
    code: string;
}

export interface DecideJoinRequestRequest {
    decision: "APPROVED" | "REJECTED";
    rejectionReason?: string | null;
}

// =============================================================================
// Class Posts (Feed)
// =============================================================================

export interface ClassPost {
    id: number;
    classId: number;
    authorId: number;
    authorName: string;
    title: string;
    content: string;
    pinned: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreateClassPostRequest {
    title: string;
    content: string;
    pinned?: boolean;
}

export interface PinClassPostRequest {
    pinned?: boolean;
}

// =============================================================================
// Class Materials
// =============================================================================

export interface ClassMaterial {
    id: number;
    classId: number;
    uploaderId: number;
    uploaderName: string;
    fileName: string;
    mimeType: string;
    sizeBytes: number;
    createdAt: string;
    updatedAt: string;
}

// =============================================================================
// Assignments & Submissions
// =============================================================================

export interface AssignmentAttachment {
    id: number;
    fileName: string;
    sizeBytes: number;
}

export interface Assignment {
    id: number;
    classId: number;
    creatorId: number;
    title: string;
    instructions: string;
    maxScore: number;
    openAt: string;
    dueAt: string;
    allowLate: boolean;
    maxSubmissions: number;
    attachments: AssignmentAttachment[];
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface AssignmentSummary {
    id: number;
    classId: number;
    title: string;
    maxScore: number;
    openAt: string;
    dueAt: string;
    allowLate: boolean;
    maxSubmissions: number;
    mySubmissionStatus?: SubmissionStatus | null;
    version: number;
    createdAt: string;
}

export interface CreateAssignmentRequest {
    title: string;
    instructions: string;
    maxScore: number;
    openAt: string;
    dueAt: string;
    allowLate: boolean;
    maxSubmissions?: number;
}

export interface UpdateAssignmentRequest {
    title: string;
    instructions: string;
    maxScore: number;
    openAt: string;
    dueAt: string;
    allowLate: boolean;
    maxSubmissions?: number;
    version?: number;
}

export interface SubmissionFile {
    id: number;
    fileName: string;
    sizeBytes: number;
}

export interface Submission {
    id: number;
    assignmentId: number;
    studentId: number;
    studentName: string;
    textAnswer: string | null;
    status: SubmissionStatus;
    late: boolean;
    submittedAt: string;
    score: number | null;
    feedback: string | null;
    gradedBy: number | null;
    gradedAt: string | null;
    files: SubmissionFile[];
    version: number;
}

export interface SubmissionSummary {
    id: number;
    assignmentId: number;
    studentId: number;
    studentName: string;
    status: SubmissionStatus;
    late: boolean;
    submittedAt: string;
    score: number | null;
    gradedAt: string | null;
}

export interface SubmitAssignmentRequest {
    textAnswer?: string;
}

export interface GradeSubmissionRequest {
    score: number;
    feedback?: string;
    status: "GRADED" | "RETURNED";
    version?: number;
}

export interface ReopenSubmissionRequest {
    reason?: string;
    version?: number;
}

// =============================================================================
// Class Tests (Timed Quiz)
// =============================================================================

export interface ClassTest {
    id: number;
    classId: number;
    quizId: number;
    title: string;
    startAt: string;
    endAt: string;
    durationMinutes: number;
    maxAttempts: number;
    shuffleQuestions: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreateClassTestRequest {
    quizId: number;
    title: string;
    startAt: string;
    endAt: string;
    durationMinutes: number;
    maxAttempts: number;
    shuffleQuestions?: boolean;
}

export interface UpdateClassTestRequest {
    title: string;
    startAt: string;
    endAt: string;
    durationMinutes: number;
    maxAttempts: number;
    shuffleQuestions?: boolean;
}

export interface StartClassTestAttemptResponse {
    attemptId: number;
    testId: number;
    quizId: number;
    startedAt: string;
    deadlineAt: string;
    questions: Array<{
        questionId: number;
        position: number;
        content: string;
        type: string;
        options: Array<{
            id: number;
            label: string;
            content: string;
        }>;
    }>;
}

export interface ClassTestSubmitResultResponse {
    attemptId: number;
    testId: number;
    status: string;
    score: number;
    maxScore: number;
    correctCount: number;
    totalQuestions: number;
    submittedAt: string;
}

export interface StudentTestResult {
    studentId: number;
    studentName: string;
    attemptsUsed: number;
    bestScore: number;
    lastSubmittedAt: string;
}

export interface ClassTestResultsResponse {
    testId: number;
    title: string;
    totalStudents: number;
    submittedCount: number;
    averageScore: number;
    highestScore: number;
    lowestScore: number;
    studentResults: StudentTestResult[];
}

// =============================================================================
// Teacher & Student Dashboard V2
// =============================================================================

export interface TeacherUpcomingAssignment {
    assignmentId: number;
    classId: number;
    className: string;
    title: string;
    maxScore: number;
    dueAt: string;
    submittedCount: number;
    totalStudents: number;
    submissionRate: number;
}

export interface TeacherRecentTestResult {
    testId: number;
    classId: number;
    className: string;
    title: string;
    startAt: string;
    endAt: string;
    submittedStudents: number;
    totalStudents: number;
    averageScore: number;
}

export interface TeacherDashboardResponse {
    totalClasses: number;
    pendingJoinRequests: number;
    upcomingAssignments: TeacherUpcomingAssignment[];
    recentTestResults: TeacherRecentTestResult[];
    unreadNotificationCount: number;
}

export interface StudentUpcomingAssignmentItem {
    assignmentId: number;
    classId: number;
    className: string;
    title: string;
    dueAt: string;
    allowLate?: boolean;
    maxScore: number;
}
