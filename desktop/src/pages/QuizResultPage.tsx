import React, { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    Award,
    CheckCircle2,
    Clock,
    HelpCircle,
    RotateCcw,
    Sparkles,
    XCircle
} from "lucide-react";
import quizService from "../services/quizService";
import { AttemptReview } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";

export const QuizResultPage: React.FC = () => {
    const { attemptId } = useParams<{ attemptId: string }>();
    const navigate = useNavigate();
    const id = Number(attemptId);

    const [attempt, setAttempt] = useState<AttemptReview | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchAttempt = async () => {
            try {
                setIsLoading(true);
                const data = await quizService.getAttemptReview(id);
                setAttempt(data);
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải kết quả bài thi.");
            } finally {
                setIsLoading(false);
            }
        };

        if (!isNaN(id)) {
            void fetchAttempt();
        }
    }, [id]);

    if (isLoading) {
        return <LoadingState text="Đang tải kết quả và giải thích chi tiết..." />;
    }

    if (error || !attempt) {
        return (
            <ErrorBanner
                title="Không thể mở kết quả"
                message={error || "Kết quả bài làm không tồn tại."}
                onRetry={() => window.location.reload()}
            />
        );
    }

    const accuracyRate = attempt.totalQuestions > 0
        ? Math.round((attempt.correctCount * 100) / attempt.totalQuestions)
        : 0;

    const isPassed = (attempt.score / (attempt.maxScore || 10)) >= 0.5;

    const formatDuration = (secs?: number) => {
        if (!secs) return "0 phút";
        const mins = Math.floor(secs / 60);
        const remSecs = secs % 60;
        return mins > 0 ? `${mins} phút ${remSecs} giây` : `${remSecs} giây`;
    };

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-20">
            {/* Top Toolbar */}
            <div className="flex items-center justify-between">
                <button
                    onClick={() => navigate("/history")}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Lịch sử luyện tập</span>
                </button>
                <div className="flex items-center gap-3">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => navigate(`/quizzes/${attempt.quizId}/take`)}
                        className="gap-1.5 text-xs"
                    >
                        <RotateCcw className="w-3.5 h-3.5" />
                        <span>Làm lại bài này</span>
                    </Button>
                </div>
            </div>

            {/* Score Summary Banner */}
            <Card className="border-slate-200 shadow-sm overflow-hidden">
                <CardContent className="p-6 md:p-8">
                    <div className="flex flex-col md:flex-row md:items-center justify-between gap-6">
                        <div className="flex items-center gap-5">
                            <div
                                className={`w-20 h-20 rounded-2xl flex flex-col items-center justify-center font-bold border-2 shrink-0 ${
                                    isPassed
                                        ? "bg-emerald-50 border-emerald-400 text-emerald-700"
                                        : "bg-rose-50 border-rose-400 text-rose-700"
                                }`}
                            >
                                <span className="text-2xl leading-none">{attempt.score}</span>
                                <span className="text-[10px] uppercase font-bold mt-1 text-slate-400">
                                    /{attempt.maxScore}
                                </span>
                            </div>

                            <div className="space-y-1">
                                <h1 className="text-xl font-bold text-slate-900">{attempt.quizTitle}</h1>
                                <p className="text-xs text-slate-500">
                                    Nộp bài lúc {new Date(attempt.submittedAt).toLocaleString("vi-VN")}
                                </p>
                                <div className="flex items-center gap-2 pt-1">
                                    <Badge variant={isPassed ? "success" : "danger"} className="text-xs">
                                        {isPassed ? "Đạt yêu cầu" : "Cần ôn tập thêm"}
                                    </Badge>
                                </div>
                            </div>
                        </div>

                        {/* Metrics Pills */}
                        <div className="grid grid-cols-2 gap-3 sm:gap-4 border-t md:border-t-0 md:border-l border-slate-100 pt-4 md:pt-0 md:pl-6">
                            <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-center">
                                <p className="text-[10px] font-semibold uppercase text-slate-400">Số câu đúng</p>
                                <p className="text-base font-bold text-slate-900 mt-0.5">
                                    {attempt.correctCount} / {attempt.totalQuestions}
                                </p>
                            </div>
                            <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 text-center">
                                <p className="text-[10px] font-semibold uppercase text-slate-400">Thời gian làm</p>
                                <p className="text-base font-bold text-slate-900 mt-0.5">
                                    {formatDuration(attempt.durationSeconds)}
                                </p>
                            </div>
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Questions Breakdown */}
            <div className="space-y-6">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <Award className="w-5 h-5 text-primary" />
                    <span>Chi tiết đáp án & Lời giải thích</span>
                </h2>

                {attempt.questions.map((question, qIdx) => {
                    const isCorrect = question.correct;

                    return (
                        <Card
                            key={question.questionId}
                            className={`border-2 shadow-sm ${
                                isCorrect ? "border-emerald-200/80 bg-white" : "border-rose-200/80 bg-white"
                            }`}
                        >
                            <CardContent className="p-6 space-y-4">
                                <div className="flex items-start justify-between gap-3">
                                    <div className="flex items-start gap-3">
                                        <span
                                            className={`w-7 h-7 rounded-lg text-xs font-bold flex items-center justify-center shrink-0 mt-0.5 ${
                                                isCorrect
                                                    ? "bg-emerald-100 text-emerald-800"
                                                    : "bg-rose-100 text-rose-800"
                                            }`}
                                        >
                                            {qIdx + 1}
                                        </span>
                                        <p className="text-sm font-semibold text-slate-900 leading-relaxed pt-0.5">
                                            {question.content}
                                        </p>
                                    </div>
                                    <Badge
                                        variant={isCorrect ? "success" : "danger"}
                                        className="gap-1 shrink-0 text-xs"
                                    >
                                        {isCorrect ? (
                                            <>
                                                <CheckCircle2 className="w-3.5 h-3.5" />
                                                <span>Đúng</span>
                                            </>
                                        ) : (
                                            <>
                                                <XCircle className="w-3.5 h-3.5" />
                                                <span>Sai</span>
                                            </>
                                        )}
                                    </Badge>
                                </div>

                                {/* Options List */}
                                <div className="space-y-2 pl-10">
                                    {question.options.map((opt) => {
                                        const isSelected = question.selectedOptionIds.includes(opt.id);
                                        const isRightOption = opt.correct;

                                        let style = "bg-white border-slate-200 text-slate-700";
                                        if (isRightOption) {
                                            style = "bg-emerald-50/70 border-emerald-400 text-emerald-900 font-semibold";
                                        } else if (isSelected && !isRightOption) {
                                            style = "bg-rose-50/70 border-rose-400 text-rose-900 font-semibold";
                                        }

                                        return (
                                            <div
                                                key={opt.id}
                                                className={`p-3 rounded-xl border text-sm flex items-center justify-between gap-3 ${style}`}
                                            >
                                                <div className="flex items-center gap-3">
                                                    <span className="w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 border bg-white/80">
                                                        {opt.label}
                                                    </span>
                                                    <span>{opt.content}</span>
                                                </div>

                                                <div className="text-xs shrink-0 font-medium">
                                                    {isSelected && isRightOption && (
                                                        <span className="text-emerald-700">✓ Lựa chọn chính xác</span>
                                                    )}
                                                    {isSelected && !isRightOption && (
                                                        <span className="text-rose-600">✗ Lựa chọn của bạn</span>
                                                    )}
                                                    {!isSelected && isRightOption && (
                                                        <span className="text-emerald-700">✓ Đáp án đúng</span>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>

                                {/* Explanation Box */}
                                {question.explanation && (
                                    <div className="ml-10 p-3.5 rounded-xl bg-blue-50/70 border border-blue-200/60 text-xs text-blue-900 space-y-1">
                                        <div className="font-semibold flex items-center gap-1 text-primary">
                                            <Sparkles className="w-3.5 h-3.5" />
                                            <span>Giải thích chi tiết:</span>
                                        </div>
                                        <p className="leading-relaxed text-slate-700">
                                            {question.explanation}
                                        </p>
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    );
                })}
            </div>
        </div>
    );
};

export default QuizResultPage;
