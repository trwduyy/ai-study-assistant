import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
    Award,
    Calendar,
    ChevronRight,
    FileQuestion,
    History,
    RotateCcw
} from "lucide-react";
import learningService from "../services/learningService";
import quizService from "../services/quizService";
import { Course, QuizAttemptSummary, QuizSummary } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { EmptyState } from "../components/common/EmptyState";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";

interface HistoryEntry {
    attempt: QuizAttemptSummary;
    quiz: QuizSummary;
}

export const QuizHistoryPage: React.FC = () => {
    const navigate = useNavigate();
    const [entries, setEntries] = useState<HistoryEntry[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchAllHistory = async () => {
            try {
                setIsLoading(true);
                const coursesRes = await learningService.getCourses(0, 50);
                const all: HistoryEntry[] = [];

                for (const course of coursesRes.items) {
                    try {
                        const quizzesRes = await quizService.listQuizzes(course.id, 0, 50);
                        for (const quiz of quizzesRes.items) {
                            try {
                                const attemptsRes = await quizService.listAttempts(quiz.id, 0, 20);
                                for (const att of attemptsRes.items) {
                                    all.push({ attempt: att, quiz });
                                }
                            } catch {
                                // Ignore empty attempts
                            }
                        }
                    } catch {
                        // Ignore failed quizzes
                    }
                }

                // Sort descending by submittedAt
                all.sort(
                    (a, b) =>
                        new Date(b.attempt.submittedAt).getTime() -
                        new Date(a.attempt.submittedAt).getTime()
                );

                setEntries(all);
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải lịch sử làm bài.");
            } finally {
                setIsLoading(false);
            }
        };

        void fetchAllHistory();
    }, []);

    if (isLoading) {
        return <LoadingState text="Đang tải lịch sử các lượt làm bài quiz..." />;
    }

    if (error) {
        return (
            <ErrorBanner
                title="Lỗi tải lịch sử"
                message={error}
                onRetry={() => window.location.reload()}
            />
        );
    }

    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                    <History className="w-6 h-6 text-primary" />
                    <span>Lịch sử luyện tập Quiz</span>
                </h1>
                <p className="text-sm text-slate-500 mt-1">
                    Xem lại kết quả các bài trắc nghiệm đã làm và giải thích câu hỏi chi tiết
                </p>
            </div>

            {entries.length === 0 ? (
                <EmptyState
                    icon={<FileQuestion className="w-8 h-8 text-slate-400" />}
                    title="Chưa có lượt làm bài nào"
                    description="Vào một khóa học và bấm 'Làm bài' để bắt đầu luyện tập trắc nghiệm bám sát tài liệu."
                    action={
                        <Button onClick={() => navigate("/courses")} className="gap-2">
                            <span>Xem danh sách khóa học</span>
                        </Button>
                    }
                />
            ) : (
                <div className="space-y-3">
                    {entries.map(({ attempt, quiz }) => {
                        const isPassed = (attempt.score / (attempt.maxScore || 10)) >= 0.5;

                        return (
                            <Card
                                key={attempt.attemptId}
                                className="hover:border-primary/40 transition-all shadow-sm"
                            >
                                <CardContent className="p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                                    <div className="flex items-center gap-4">
                                        <div
                                            className={`w-12 h-12 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 border ${
                                                isPassed
                                                    ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                                    : "bg-rose-50 text-rose-700 border-rose-200"
                                            }`}
                                        >
                                            {attempt.score}/{attempt.maxScore}
                                        </div>

                                        <div className="space-y-1 min-w-0">
                                            <h3 className="font-semibold text-slate-900 text-sm truncate">
                                                {quiz.title}
                                            </h3>
                                            <div className="flex items-center gap-2 text-xs text-slate-500">
                                                <span>
                                                    {attempt.correctCount}/{attempt.totalQuestions} câu đúng
                                                </span>
                                                <span>•</span>
                                                <span>
                                                    {new Date(attempt.submittedAt).toLocaleString("vi-VN")}
                                                </span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="flex items-center gap-2 shrink-0">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => navigate(`/attempts/${attempt.attemptId}`)}
                                            className="gap-1 text-xs"
                                        >
                                            <span>Xem kết quả</span>
                                            <ChevronRight className="w-3.5 h-3.5" />
                                        </Button>
                                        <Button
                                            size="sm"
                                            onClick={() => navigate(`/quizzes/${quiz.id}/take`)}
                                            className="gap-1 text-xs"
                                        >
                                            <RotateCcw className="w-3.5 h-3.5" />
                                            <span>Làm lại</span>
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
                </div>
            )}
        </div>
    );
};

export default QuizHistoryPage;
