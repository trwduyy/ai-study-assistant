import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Calendar, ChevronRight, FileQuestion, History } from "lucide-react";
import courseService from "../services/courseService";
import quizService from "../services/quizService";
import { QuizAttemptHistory } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

interface EnrichedAttempt extends QuizAttemptHistory {
    quizTitle: string;
    courseTitle: string;
}

export const QuizHistoryPage: React.FC = () => {
    const [history, setHistory] = useState<EnrichedAttempt[]>([]);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchHistory = async () => {
            try {
                setIsLoading(true);
                const courses = await courseService.getCourses();
                const allAttempts: EnrichedAttempt[] = [];

                for (const course of courses) {
                    try {
                        const quizzes = await quizService.getQuizzesByCourse(course.id);
                        for (const quiz of quizzes) {
                            try {
                                const attempts = await quizService.getQuizAttempts(quiz.id);
                                for (const att of attempts) {
                                    allAttempts.push({
                                        ...att,
                                        quizTitle: quiz.title,
                                        courseTitle: course.title
                                    });
                                }
                            } catch {
                                // Ignore empty or failed attempts lookup for specific quiz
                            }
                        }
                    } catch {
                        // Ignore failed quizzes lookup
                    }
                }

                // Sort attempts by submittedAt descending
                allAttempts.sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime());
                setHistory(allAttempts);
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải lịch sử làm bài.");
            } finally {
                setIsLoading(false);
            }
        };

        fetchHistory();
    }, []);

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang tải lịch sử luyện tập..." className="py-20" />;
    }

    return (
        <div className="space-y-6">
            <div>
                <h2 className="text-xl font-bold text-slate-800 flex items-center gap-2">
                    <History className="w-5 h-5 text-indigo-600" />
                    <span>Lịch sử luyện tập Quiz</span>
                </h2>
                <p className="text-sm text-slate-500 mt-0.5">
                    Theo dõi điểm số và xem lại chi tiết lời giải thích các bài trắc nghiệm bạn đã hoàn thành
                </p>
            </div>

            {error && (
                <div className="p-4 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm">
                    {error}
                </div>
            )}

            {history.length > 0 ? (
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden divide-y divide-slate-100">
                    {history.map((att) => {
                        const isPassed = att.score >= 50;

                        return (
                            <div
                                key={att.attemptId}
                                className="p-4 md:p-5 flex items-center justify-between gap-4 hover:bg-slate-50/80 transition"
                            >
                                <div className="flex items-center gap-4 min-w-0">
                                    <div
                                        className={`w-12 h-12 rounded-xl flex items-center justify-center font-bold text-sm shrink-0 border ${
                                            isPassed
                                                ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                                                : "bg-rose-50 text-rose-700 border-rose-200"
                                        }`}
                                    >
                                        {att.score}
                                    </div>

                                    <div className="min-w-0">
                                        <div className="flex items-center gap-2">
                                            <span className="text-[10px] font-semibold text-indigo-700 bg-indigo-50 px-2 py-0.5 rounded-full border border-indigo-100">
                                                {att.courseTitle}
                                            </span>
                                            <span className="text-xs text-slate-400 flex items-center gap-1">
                                                <Calendar className="w-3 h-3" />
                                                {new Date(att.submittedAt).toLocaleDateString("vi-VN")}
                                            </span>
                                        </div>

                                        <h4 className="text-sm font-bold text-slate-800 truncate mt-1">
                                            {att.quizTitle}
                                        </h4>

                                        <p className="text-xs text-slate-500 mt-0.5">
                                            Đúng {att.correctCount}/{att.totalQuestions} câu
                                        </p>
                                    </div>
                                </div>

                                <Link
                                    to={`/attempts/${att.attemptId}`}
                                    className="inline-flex items-center gap-1 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-indigo-50 text-slate-700 hover:text-indigo-600 font-semibold text-xs transition shrink-0"
                                >
                                    <span>Xem chi tiết</span>
                                    <ChevronRight className="w-4 h-4" />
                                </Link>
                            </div>
                        );
                    })}
                </div>
            ) : (
                <div className="bg-white rounded-2xl border border-dashed border-slate-300 p-12 text-center text-slate-400">
                    <FileQuestion className="w-12 h-12 text-slate-300 mx-auto mb-2" />
                    <p className="text-sm font-semibold text-slate-700">Chưa có bài thi trắc nghiệm nào</p>
                    <p className="text-xs text-slate-400 mt-1 max-w-sm mx-auto mb-6">
                        Hãy vào một khóa học bất kỳ và nhấn &ldquo;Tạo đề thi AI&rdquo; để kiểm tra kiến thức của bạn.
                    </p>
                    <Link
                        to="/courses"
                        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition"
                    >
                        Khám phá khóa học
                    </Link>
                </div>
            )}
        </div>
    );
};

export default QuizHistoryPage;
