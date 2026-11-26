import React from "react";
import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { Toaster } from "sonner";
import { AuthProvider, useAuth } from "./contexts/AuthContext";
import { WebSocketProvider } from "./contexts/WebSocketContext";
import { NotificationProvider } from "./contexts/NotificationContext";
import ProtectedRoute from "./components/common/ProtectedRoute";
import MainLayout from "./components/layout/MainLayout";

import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import DashboardPage from "./pages/DashboardPage";
import CoursesPage from "./pages/CoursesPage";
import CourseDetailPage from "./pages/CourseDetailPage";
import MaterialStudyPage from "./pages/MaterialStudyPage";
import QuizTakePage from "./pages/QuizTakePage";
import QuizResultPage from "./pages/QuizResultPage";
import QuizHistoryPage from "./pages/QuizHistoryPage";
import FlashcardsPage from "./pages/FlashcardsPage";
import FlashcardReviewPage from "./pages/FlashcardReviewPage";
import ProfilePage from "./pages/ProfilePage";
import ClassesPage from "./pages/ClassesPage";
import ClassDetailPage from "./pages/ClassDetailPage";
import AssignmentsListPage from "./pages/AssignmentsListPage";
import AssignmentDetailPage from "./pages/AssignmentDetailPage";
import TestsListPage from "./pages/TestsListPage";
import ClassTestTakePage from "./pages/ClassTestTakePage";
import ClassTestResultsPage from "./pages/ClassTestResultsPage";
import TeacherDashboardPage from "./pages/TeacherDashboardPage";
import TeacherMeetingsPage from "./pages/TeacherMeetingsPage";
import ClassStatisticsPage from "./pages/ClassStatisticsPage";
import MessagesPage from "./pages/MessagesPage";
import NotificationsPage from "./pages/NotificationsPage";
import CalendarPage from "./pages/CalendarPage";
import StudyPlanPage from "./pages/StudyPlanPage";
import AdminUsersPage from "./pages/admin/AdminUsersPage";
import AdminClassesPage from "./pages/admin/AdminClassesPage";
import AdminGovernancePage from "./pages/admin/AdminGovernancePage";
import ForbiddenPage from "./pages/ForbiddenPage";
import NotFoundPage from "./pages/NotFoundPage";

const RoleHomeRedirect: React.FC = () => {
    const { user } = useAuth();
    if (user?.role === "ADMIN") {
        return <Navigate to="/admin/users" replace />;
    }
    return <Navigate to="/dashboard" replace />;
};

const App: React.FC = () => {
    return (
        <AuthProvider>
            <WebSocketProvider>
                <HashRouter>
                    <NotificationProvider>
                        <Routes>
                            {/* Public Routes */}
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/register" element={<RegisterPage />} />

                            {/* Protected Routes inside MainLayout */}
                            <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
                                <Route index element={<RoleHomeRedirect />} />
                                <Route path="dashboard" element={<DashboardPage />} />
                                <Route
                                    path="teacher/dashboard"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <TeacherDashboardPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route path="courses" element={<CoursesPage />} />
                                <Route path="courses/:courseId" element={<CourseDetailPage />} />
                                <Route path="materials/:materialId" element={<MaterialStudyPage />} />
                                <Route path="quizzes/:quizId/take" element={<QuizTakePage />} />
                                <Route path="attempts/:attemptId" element={<QuizResultPage />} />
                                <Route path="history" element={<QuizHistoryPage />} />
                                <Route path="flashcards" element={<FlashcardsPage />} />
                                <Route path="flashcards/:deckId/review" element={<FlashcardReviewPage />} />
                                <Route path="ai-materials" element={<CoursesPage />} />
                                <Route path="profile" element={<ProfilePage />} />

                                {/* Classroom & Assignment Modules (FE-2A) */}
                                <Route path="classes" element={<ClassesPage />} />
                                <Route path="classes/:classId" element={<ClassDetailPage />} />
                                <Route
                                    path="my-classes"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <ClassesPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route path="assignments" element={<AssignmentsListPage />} />
                                <Route path="assignments/:assignmentId" element={<AssignmentDetailPage />} />
                                <Route
                                    path="tests"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <TestsListPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route path="tests/:testId/take" element={<ClassTestTakePage />} />
                                <Route
                                    path="tests/:testId/results"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <ClassTestResultsPage />
                                        </ProtectedRoute>
                                    }
                                />

                                {/* Teacher Meetings & Statistics (FE-3B) */}
                                <Route
                                    path="meetings"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <TeacherMeetingsPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="class-statistics"
                                    element={
                                        <ProtectedRoute allowedRoles={["TEACHER", "ADMIN"]}>
                                            <ClassStatisticsPage />
                                        </ProtectedRoute>
                                    }
                                />

                                {/* Real-time Chat & Notification Modules (FE-2B) */}
                                <Route path="messages" element={<MessagesPage />} />
                                <Route path="messages/:conversationId" element={<MessagesPage />} />
                                <Route path="notifications" element={<NotificationsPage />} />
                                <Route path="notifications/settings" element={<NotificationsPage />} />

                                {/* Schedule, Calendar, Goals, Tasks & AI Study Plan Modules (FE-3A) */}
                                <Route path="schedule" element={<CalendarPage />} />
                                <Route path="calendar" element={<CalendarPage />} />
                                <Route path="study-plan" element={<StudyPlanPage />} />

                                {/* Admin Governance Routes (FE-3B) */}
                                <Route
                                    path="admin/users"
                                    element={
                                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                                            <AdminUsersPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="admin/classes"
                                    element={
                                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                                            <AdminClassesPage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="admin/reports"
                                    element={
                                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                                            <AdminGovernancePage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="admin/settings"
                                    element={
                                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                                            <AdminGovernancePage />
                                        </ProtectedRoute>
                                    }
                                />
                                <Route
                                    path="admin/audit-logs"
                                    element={
                                        <ProtectedRoute allowedRoles={["ADMIN"]}>
                                            <AdminGovernancePage />
                                        </ProtectedRoute>
                                    }
                                />

                                {/* Legacy Admin path aliases */}
                                <Route path="users" element={<Navigate to="/admin/users" replace />} />
                                <Route path="reports" element={<Navigate to="/admin/reports" replace />} />
                                <Route path="settings" element={<Navigate to="/admin/settings" replace />} />

                                {/* Error Pages */}
                                <Route path="403" element={<ForbiddenPage />} />
                                <Route path="*" element={<NotFoundPage />} />
                            </Route>

                            {/* Fallback route */}
                            <Route path="*" element={<RoleHomeRedirect />} />
                        </Routes>
                        <Toaster richColors position="top-right" closeButton />
                    </NotificationProvider>
                </HashRouter>
            </WebSocketProvider>
        </AuthProvider>
    );
};

export default App;
