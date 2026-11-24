import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";
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
import ProfilePage from "./pages/ProfilePage";

const App: React.FC = () => {
    return (
        <AuthProvider>
            <BrowserRouter>
                <Routes>
                    {/* Public Routes */}
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/register" element={<RegisterPage />} />

                    {/* Protected Routes inside MainLayout */}
                    <Route
                        path="/"
                        element={
                            <ProtectedRoute>
                                <MainLayout />
                            </ProtectedRoute>
                        }
                    >
                        <Route index element={<Navigate to="/dashboard" replace />} />
                        <Route path="dashboard" element={<DashboardPage />} />
                        <Route path="courses" element={<CoursesPage />} />
                        <Route path="courses/:courseId" element={<CourseDetailPage />} />
                        <Route path="materials/:materialId" element={<MaterialStudyPage />} />
                        <Route path="quizzes/:quizId/take" element={<QuizTakePage />} />
                        <Route path="attempts/:attemptId" element={<QuizResultPage />} />
                        <Route path="history" element={<QuizHistoryPage />} />
                        <Route path="profile" element={<ProfilePage />} />
                    </Route>

                    {/* Fallback route */}
                    <Route path="*" element={<Navigate to="/dashboard" replace />} />
                </Routes>
            </BrowserRouter>
        </AuthProvider>
    );
};

export default App;
