import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    CheckCircle2,
    Clock,
    FileQuestion,
    HelpCircle,
    Save,
    Send
} from "lucide-react";
import quizService from "../services/quizService";
import { QuizDetail, QuizQuestion } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { toast } from "sonner";

export const QuizTakePage: React.FC = () => {
    const { quizId } = useParams<{ quizId: string }>();
    const navigate = useNavigate();
    const id = Number(quizId);

    const [quiz, setQuiz] = useState<QuizDetail | null>(null);
    const [attemptId, setAttemptId] = useState<number | null>(null);
    const [attemptVersion, setAttemptVersion] = useState<number>(0);

    // Selected options map: questionId -> array of selected optionIds
    const [selectedAnswers, setSelectedAnswers] = useState<Record<number, number[]>>({});
    const [isDraftSaving, setIsDraftSaving] = useState<boolean>(false);
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Timer state in seconds
    const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);

    // Idempotency key preserved per take session
    const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

    useEffect(() => {
        const initializeQuizTake = async () => {
            try {
                setIsLoading(true);
                const [quizData, attemptData] = await Promise.all([
                    quizService.getQuiz(id),
                    quizService.startAttempt(id)
                ]);

                setQuiz(quizData);
                setAttemptId(attemptData.attemptId);
                setAttemptVersion(attemptData.version);

                // Populate any initial draft answers
                if (attemptData.answers && attemptData.answers.length > 0) {
                    const initial: Record<number, number[]> = {};
                    attemptData.answers.forEach((ans) => {
                        initial[ans.questionId] = ans.selectedOptionIds;
                    });
                    setSelectedAnswers(initial);
                }

                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể khởi tạo lượt làm bài.");
            } finally {
                setIsLoading(false);
            }
        };

        if (!isNaN(id)) {
            void initializeQuizTake();
        }
    }, [id]);

    // Timer interval
    useEffect(() => {
        if (!quiz || isSubmitting) return;

        const timer = setInterval(() => {
            setElapsedSeconds((prev) => prev + 1);
        }, 1000);

        return () => clearInterval(timer);
    }, [quiz, isSubmitting]);

    const formatTimer = (totalSeconds: number) => {
        const mins = Math.floor(totalSeconds / 60);
        const secs = totalSeconds % 60;
        return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
    };

    // Handle Option Selection & Auto-save draft
    const handleSelectOption = async (questionId: number, optionId: number) => {
        if (!attemptId) return;

        const updatedAnswers = {
            ...selectedAnswers,
            [questionId]: [optionId]
        };
        setSelectedAnswers(updatedAnswers);

        try {
            setIsDraftSaving(true);
            const payload = Object.entries(updatedAnswers).map(([qId, optIds]) => ({
                questionId: Number(qId),
                selectedOptionIds: optIds
            }));

            const draftRes = await quizService.saveAttemptDraft(id, attemptId, {
                version: attemptVersion,
                answers: payload
            });
            setAttemptVersion(draftRes.version);
        } catch {
            // Silently ignore transient draft save failures
        } finally {
            setIsDraftSaving(false);
        }
    };

    const answeredCount = Object.keys(selectedAnswers).filter(
        (qId) => (selectedAnswers[Number(qId)] || []).length > 0
    ).length;
    const totalQuestions = quiz?.questions.length || 0;
    const progressPercent = totalQuestions > 0 ? Math.round((answeredCount * 100) / totalQuestions) : 0;

    const handleSubmitQuiz = async () => {
        if (!quiz || !attemptId || isSubmitting) return;

        if (answeredCount < totalQuestions) {
            const unanswered = totalQuestions - answeredCount;
            const confirm = window.confirm(
                `Bạn còn ${unanswered} câu hỏi chưa trả lời. Bạn có chắc chắn muốn nộp bài ngay bây giờ không?`
            );
            if (!confirm) return;
        }

        try {
            setIsSubmitting(true);
            const answersPayload = quiz.questions.map((q) => ({
                questionId: q.id,
                selectedOptionIds: selectedAnswers[q.id] || []
            }));

            const result = await quizService.submitAttempt(
                id,
                attemptId,
                {
                    version: attemptVersion,
                    durationSeconds: elapsedSeconds,
                    answers: answersPayload
                },
                idempotencyKeyRef.current
            );

            toast.success("Nộp bài thi thành công!");
            navigate(`/attempts/${result.attemptId}`);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Nộp bài thi thất bại. Vui lòng bấm thử lại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return <LoadingState text="Đang chuẩn bị đề thi và tạo lượt làm bài..." />;
    }

    if (error || !quiz) {
        return (
            <ErrorBanner
                title="Không thể làm bài thi"
                message={error || "Bài thi không tồn tại."}
                onRetry={() => window.location.reload()}
            />
        );
    }

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-20">
            {/* Top Toolbar */}
            <div className="flex items-center justify-between">
                <button
                    onClick={() => navigate(-1)}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Quay lại</span>
                </button>
                <div className="flex items-center gap-4 text-xs font-semibold text-slate-600">
                    {isDraftSaving && (
                        <span className="flex items-center gap-1 text-slate-400 text-[11px] animate-pulse">
                            <Save className="w-3.5 h-3.5" />
                            <span>Đang lưu nháp...</span>
                        </span>
                    )}
                    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white border border-slate-200 shadow-sm">
                        <Clock className="w-4 h-4 text-primary" />
                        <span className="font-mono text-sm text-slate-900 font-bold">
                            {formatTimer(elapsedSeconds)}
                        </span>
                    </div>
                </div>
            </div>

            {/* Quiz Info Header */}
            <Card className="border-slate-200 shadow-sm">
                <CardContent className="p-6">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <h1 className="text-xl font-bold text-slate-900">{quiz.title}</h1>
                                <Badge
                                    variant={
                                        quiz.difficulty === "EASY"
                                            ? "success"
                                            : quiz.difficulty === "HARD"
                                            ? "danger"
                                            : "warning"
                                    }
                                    className="text-xs"
                                >
                                    {quiz.difficulty === "EASY"
                                        ? "Dễ"
                                        : quiz.difficulty === "HARD"
                                        ? "Khó"
                                        : "Vừa"}
                                </Badge>
                            </div>
                            <p className="text-xs text-slate-500">
                                Tổng số {totalQuestions} câu hỏi trắc nghiệm khách quan
                            </p>
                        </div>

                        <div className="flex items-center gap-3">
                            <Button
                                onClick={handleSubmitQuiz}
                                isLoading={isSubmitting}
                                className="gap-2 px-6"
                            >
                                <Send className="w-4 h-4" />
                                <span>Nộp bài ngay</span>
                            </Button>
                        </div>
                    </div>

                    {/* Progress Bar */}
                    <div className="mt-5 space-y-1.5">
                        <div className="flex justify-between text-xs font-semibold text-slate-600">
                            <span>
                                Tiến độ: {answeredCount} / {totalQuestions} câu đã làm
                            </span>
                            <span>{progressPercent}%</span>
                        </div>
                        <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
                            <div
                                className="h-full bg-primary transition-all duration-300"
                                style={{ width: `${progressPercent}%` }}
                            />
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* Questions List */}
            <div className="space-y-6">
                {quiz.questions
                    .slice()
                    .sort((a, b) => a.position - b.position)
                    .map((question, qIdx) => {
                        const userSelectedIds = selectedAnswers[question.id] || [];

                        return (
                            <Card key={question.id} className="border-slate-200 shadow-sm">
                                <CardContent className="p-6 space-y-4">
                                    <div className="flex items-start gap-3">
                                        <span className="w-7 h-7 rounded-lg bg-blue-50 text-primary text-xs font-bold flex items-center justify-center shrink-0 border border-blue-100 mt-0.5">
                                            {qIdx + 1}
                                        </span>
                                        <p className="text-sm font-semibold text-slate-900 leading-relaxed pt-0.5">
                                            {question.content}
                                        </p>
                                    </div>

                                    {/* Options */}
                                    <div className="grid grid-cols-1 gap-2.5 pt-1 pl-10">
                                        {question.options.map((opt) => {
                                            const isSelected = userSelectedIds.includes(opt.id);

                                            return (
                                                <button
                                                    key={opt.id}
                                                    type="button"
                                                    onClick={() => handleSelectOption(question.id, opt.id)}
                                                    className={`w-full text-left p-3.5 rounded-xl border text-sm transition flex items-center gap-3.5 ${
                                                        isSelected
                                                            ? "bg-blue-50/80 border-primary text-primary font-medium shadow-sm"
                                                            : "bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                                                    }`}
                                                >
                                                    <div
                                                        className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 border ${
                                                            isSelected
                                                                ? "bg-primary text-white border-primary"
                                                                : "border-slate-300 text-slate-500 bg-slate-50"
                                                        }`}
                                                    >
                                                        {opt.label}
                                                    </div>
                                                    <span className="flex-1 leading-normal">
                                                        {opt.content}
                                                    </span>
                                                </button>
                                            );
                                        })}
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
            </div>

            {/* Bottom Submit Sticky Bar */}
            <div className="fixed bottom-0 left-0 right-0 p-4 bg-white/95 backdrop-blur border-t border-slate-200 shadow-elevated z-20">
                <div className="max-w-4xl mx-auto flex items-center justify-between">
                    <span className="text-xs text-slate-600 font-medium">
                        Đã chọn {answeredCount}/{totalQuestions} câu • Hệ thống tự động lưu nháp
                    </span>
                    <Button
                        onClick={handleSubmitQuiz}
                        isLoading={isSubmitting}
                        className="gap-2 px-8"
                    >
                        <Send className="w-4 h-4" />
                        <span>Nộp bài</span>
                    </Button>
                </div>
            </div>
        </div>
    );
};

export default QuizTakePage;
