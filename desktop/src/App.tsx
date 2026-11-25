import React from "react";
import { HashRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";
import { WebSocketProvider } from "./contexts/WebSocketContext";
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
import { PlaceholderPage } from "./pages/PlaceholderPage";
import { Toaster } from "sonner";

const App: React.FC = () => {
    return (
        <AuthProvider>
            <WebSocketProvider>
                <HashRouter>
                    <Routes>
                        {/* Public Routes */}
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/register" element={<RegisterPage />} />

                        {/* Protected Routes inside MainLayout */}
                        <Route path="/" element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
                            <Route index element={<Navigate to="/dashboard" replace />} />
                            <Route path="dashboard" element={<DashboardPage />} />
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

                            {/* Remaining Phased Placeholders */}
                            <Route path="classes" element={<PlaceholderPage title="Lớp học" description="Quản lý lớp và gửi yêu cầu tham gia bằng mã lớp." />} />
                            <Route path="schedule" element={<PlaceholderPage title="Lịch học" description="Theo dõi lịch học, sự kiện và nhắc việc cá nhân." />} />
                            <Route path="assignments" element={<PlaceholderPage title="Bài tập" description="Xem hạn nộp, nộp bài và theo dõi kết quả chấm." />} />
                            <Route path="messages" element={<PlaceholderPage title="Tin nhắn" description="Trao đổi với lớp học và giảng viên theo thời gian thực." />} />
                            <Route path="study-plan" element={<PlaceholderPage title="Kế hoạch học" description="Tạo mục tiêu và kế hoạch học tập cá nhân." />} />
                            <Route path="my-classes" element={<ProtectedRoute allowedRoles={["TEACHER"]}><PlaceholderPage title="Lớp của tôi" description="Tạo và quản lý lớp học do bạn phụ trách." /></ProtectedRoute>} />
                            <Route path="tests" element={<ProtectedRoute allowedRoles={["TEACHER"]}><PlaceholderPage title="Bài kiểm tra" description="Tạo, lên lịch và theo dõi bài kiểm tra của lớp." /></ProtectedRoute>} />
                            <Route path="meetings" element={<ProtectedRoute allowedRoles={["TEACHER"]}><PlaceholderPage title="Cuộc họp" description="Quản lý cuộc họp, lịch học trực tuyến và liên kết tham gia." /></ProtectedRoute>} />
                            <Route path="class-statistics" element={<ProtectedRoute allowedRoles={["TEACHER"]}><PlaceholderPage title="Thống kê lớp" description="Theo dõi tiến độ, điểm số và mức độ tham gia của lớp." /></ProtectedRoute>} />
                            <Route path="users" element={<ProtectedRoute allowedRoles={["ADMIN"]}><PlaceholderPage title="Người dùng" description="Quản lý tài khoản, trạng thái và vai trò người dùng." /></ProtectedRoute>} />
                            <Route path="admin/classes" element={<ProtectedRoute allowedRoles={["ADMIN"]}><PlaceholderPage title="Lớp học" description="Giám sát danh sách lớp và cấu hình quản trị." /></ProtectedRoute>} />
                            <Route path="reports" element={<ProtectedRoute allowedRoles={["ADMIN"]}><PlaceholderPage title="Báo cáo" description="Tổng hợp báo cáo vận hành và học tập." /></ProtectedRoute>} />
                            <Route path="settings" element={<ProtectedRoute allowedRoles={["ADMIN"]}><PlaceholderPage title="Cấu hình" description="Thiết lập cấu hình dùng chung của hệ thống." /></ProtectedRoute>} />
                        </Route>

                        {/* Fallback route */}
                        <Route path="*" element={<Navigate to="/dashboard" replace />} />
                    </Routes>
                    <Toaster richColors position="top-right" closeButton />
                </HashRouter>
            </WebSocketProvider>
        </AuthProvider>
    );
};

export default App;
