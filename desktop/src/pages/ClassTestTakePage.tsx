import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    CheckCircle2,
    Clock,
    FileQuestion,
    HelpCircle,
    Send,
    Award,
    AlertTriangle
} from "lucide-react";
import { toast } from "sonner";
import testService from "../services/testService";
import { StartClassTestAttemptResponse, ClassTestSubmitResultResponse } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";

export const ClassTestTakePage: React.FC = () => {
    const { testId } = useParams<{ testId: string }>();
    const numericTestId = Number(testId);
    const navigate = useNavigate();

    const [attemptData, setAttemptData] = useState<StartClassTestAttemptResponse | null>(null);
    const [selectedAnswers, setSelectedAnswers] = useState<Record<number, number[]>>({});
    const [remainingSeconds, setRemainingSeconds] = useState<number>(0);
    const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);

    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [submitResult, setSubmitResult] = useState<ClassTestSubmitResultResponse | null>(null);
    const [error, setError] = useState<string | null>(null);

    // Keep idempotency key unchanged on network retry
    const idempotencyKeyRef = useRef<string>(crypto.randomUUID());
    const hasAutoSubmittedRef = useRef<boolean>(false);

    // Start Attempt
    useEffect(() => {
        const startTestAttempt = async () => {
            if (!numericTestId) return;
            try {
                setIsLoading(true);
                const data = await testService.startAttempt(numericTestId);
                setAttemptData(data);

                // Calculate initial countdown
                const deadline = new Date(data.deadlineAt).getTime();
                const now = Date.now();
                const diffSec = Math.max(0, Math.floor((deadline - now) / 1000));
                setRemainingSeconds(diffSec);

                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể bắt đầu làm bài kiểm tra.");
            } finally {
                setIsLoading(false);
            }
        };

        void startTestAttempt();
    }, [numericTestId]);

    // Countdown Timer & Auto-Submit on Timeout
    useEffect(() => {
        if (!attemptData || isSubmitting || submitResult) return;

        const timer = setInterval(() => {
            setElapsedSeconds((prev) => prev + 1);

            const deadline = new Date(attemptData.deadlineAt).getTime();
            const now = Date.now();
            const diffSec = Math.max(0, Math.floor((deadline - now) / 1000));
            setRemainingSeconds(diffSec);

            if (diffSec <= 0 && !hasAutoSubmittedRef.current) {
                hasAutoSubmittedRef.current = true;
                clearInterval(timer);
                toast.warning("Đã hết giờ làm bài! Hệ thống đang tự động nộp bài của bạn...");
                void doSubmit(true);
            }
        }, 1000);

        return () => clearInterval(timer);
    }, [attemptData, isSubmitting, submitResult, selectedAnswers]);

    const formatCountdown = (totalSec: number) => {
        const mins = Math.floor(totalSec / 60);
        const secs = totalSec % 60;
        return `${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
    };

    // Option selection
    const handleSelectOption = (questionId: number, optionId: number) => {
        if (isSubmitting || submitResult) return;
        setSelectedAnswers((prev) => ({
            ...prev,
            [questionId]: [optionId]
        }));
    };

    // Submit handler
    const doSubmit = async (isAuto = false) => {
        if (!attemptData || isSubmitting) return;

        try {
            setIsSubmitting(true);
            const formattedAnswers = Object.entries(selectedAnswers).map(([qId, optIds]) => ({
                questionId: Number(qId),
                selectedOptionIds: optIds
            }));

            const res = await testService.submitAttempt(
                numericTestId,
                attemptData.attemptId,
                {
                    answers: formattedAnswers,
                    durationSeconds: elapsedSeconds
                },
                idempotencyKeyRef.current
            );

            setSubmitResult(res);
            toast.success(isAuto ? "Đã tự động nộp bài kiểm tra thành công!" : "Nộp bài kiểm tra thành công!");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Nộp bài thất bại. Bạn có thể nhấn thử lại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return <LoadingState label="Đang khởi tạo lượt làm bài kiểm tra..." />;
    }

    if (error || !attemptData) {
        return (
            <div className="max-w-xl mx-auto p-6 space-y-4">
                <ErrorBanner
                    title="Không thể làm bài kiểm tra"
                    message={error || "Bài kiểm tra chưa mở hoặc bạn đã hết số lượt làm bài cho phép."}
                />
                <Button
                    variant="outline"
                    onClick={() => navigate(-1)}
                    className="gap-2"
                >
                    <ArrowLeft className="w-4 h-4" />
                    Quay lại
                </Button>
            </div>
        );
    }

    // Success Result Screen
    if (submitResult) {
        const percentage = Math.round((submitResult.correctCount / submitResult.totalQuestions) * 100);

        return (
            <div className="max-w-2xl mx-auto p-6 space-y-6">
                <Card className="border-slate-200 shadow-md overflow-hidden text-center">
                    <div className="bg-primary/5 p-8 border-b border-primary/10 space-y-3">
                        <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto shadow-inner">
                            <Award className="w-8 h-8" />
                        </div>
                        <h1 className="text-2xl font-bold text-slate-900">
                            Hoàn thành bài kiểm tra!
                        </h1>
                        <p className="text-sm text-slate-500">
                            Hệ thống đã ghi nhận bài nộp và chấm điểm tự động.
                        </p>
                    </div>

                    <CardContent className="p-8 space-y-6">
                        <div className="grid grid-cols-2 gap-4">
                            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
                                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold">
                                    Điểm số đạt được
                                </p>
                                <p className="text-3xl font-extrabold text-primary mt-1">
                                    {submitResult.score} <span className="text-sm font-normal text-slate-400">/ {submitResult.maxScore}</span>
                                </p>
                            </div>

                            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100">
                                <p className="text-xs text-slate-500 uppercase tracking-wider font-semibold">
                                    Số câu trả lời đúng
                                </p>
                                <p className="text-3xl font-extrabold text-emerald-600 mt-1">
                                    {submitResult.correctCount} <span className="text-sm font-normal text-slate-400">/ {submitResult.totalQuestions}</span>
                                </p>
                            </div>
                        </div>

                        <div className="space-y-1.5">
                            <div className="flex justify-between text-xs text-slate-600">
                                <span>Tỷ lệ chính xác</span>
                                <span className="font-bold">{percentage}%</span>
                            </div>
                            <div className="w-full h-2.5 bg-slate-100 rounded-full overflow-hidden">
                                <div
                                    className="h-full bg-primary rounded-full transition-all duration-500"
                                    style={{ width: `${percentage}%` }}
                                />
                            </div>
                        </div>

                        <div className="pt-4 flex justify-center gap-3">
                            <Button
                                variant="default"
                                onClick={() => navigate(-1)}
                                className="gap-2"
                            >
                                <ArrowLeft className="w-4 h-4" />
                                Quay lại lớp học
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            </div>
        );
    }

    const answeredCount = Object.keys(selectedAnswers).length;
    const totalCount = attemptData.questions.length;
    const isWarningTime = remainingSeconds > 0 && remainingSeconds <= 120; // 2 minutes warning

    return (
        <div className="space-y-6 p-6 max-w-4xl mx-auto">
            {/* Sticky Header with Timer & Progress */}
            <div className="sticky top-2 z-20 bg-white/95 backdrop-blur-md p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="space-y-1">
                    <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-xs text-slate-600">
                            Bài kiểm tra định thời
                        </Badge>
                        <span className="text-xs text-slate-500 font-medium">
                            Đã trả lời: <strong>{answeredCount}</strong> / {totalCount} câu
                        </span>
                    </div>
                    <div className="w-48 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-primary rounded-full transition-all duration-300"
                            style={{ width: `${(answeredCount / totalCount) * 100}%` }}
                        />
                    </div>
                </div>

                <div className="flex items-center gap-4">
                    {/* Countdown Timer with warning */}
                    <div
                        className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg border font-mono font-bold text-base transition-colors ${
                            isWarningTime
                                ? "bg-rose-50 border-rose-300 text-rose-600 animate-pulse"
                                : remainingSeconds <= 300
                                ? "bg-amber-50 border-amber-300 text-amber-700"
                                : "bg-slate-50 border-slate-200 text-slate-800"
                        }`}
                    >
                        <Clock className={`w-4 h-4 ${isWarningTime ? "text-rose-600 animate-spin" : ""}`} />
                        <span>{formatCountdown(remainingSeconds)}</span>
                        {isWarningTime && (
                            <span className="text-[10px] font-sans font-semibold text-rose-600">
                                (Sắp hết giờ!)
                            </span>
                        )}
                    </div>

                    <Button
                        variant="default"
                        disabled={isSubmitting}
                        onClick={() => {
                            if (window.confirm("Bạn có chắc chắn muốn nộp bài kiểm tra ngay bây giờ không?")) {
                                void doSubmit(false);
                            }
                        }}
                        className="gap-2 bg-emerald-600 hover:bg-emerald-700 text-xs h-9"
                    >
                        <Send className="w-3.5 h-3.5" />
                        {isSubmitting ? "Đang nộp..." : "Nộp bài thi"}
                    </Button>
                </div>
            </div>

            {/* Warning callout when < 2 mins */}
            {isWarningTime && (
                <div className="p-3.5 rounded-xl border border-rose-200 bg-rose-50 flex items-center gap-3 text-xs text-rose-900 font-semibold animate-bounce">
                    <AlertTriangle className="w-5 h-5 text-rose-600 shrink-0" />
                    <span>Thời gian làm bài còn dưới 2 phút! Vui lòng kiểm tra lại đáp án và chuẩn bị nộp bài. Hệ thống sẽ tự động nộp khi đồng hồ về 00:00.</span>
                </div>
            )}

            {/* Questions List */}
            <div className="space-y-5">
                {attemptData.questions.map((q, idx) => {
                    const isSelected = !!selectedAnswers[q.questionId];

                    return (
                        <Card
                            key={q.questionId}
                            className={`border-slate-200 shadow-sm transition-all ${
                                isSelected ? "border-primary/40 bg-white" : "border-slate-200 bg-slate-50/30"
                            }`}
                        >
                            <CardContent className="p-6 space-y-4">
                                <div className="flex items-start gap-3">
                                    <span className="w-7 h-7 rounded-lg bg-primary/10 text-primary text-xs font-bold flex items-center justify-center shrink-0">
                                        {idx + 1}
                                    </span>
                                    <h3 className="font-semibold text-base text-slate-900 leading-snug">
                                        {q.content}
                                    </h3>
                                </div>

                                {/* Options */}
                                <div className="space-y-2.5 pl-10">
                                    {q.options.map((opt) => {
                                        const checked = selectedAnswers[q.questionId]?.includes(opt.id);

                                        return (
                                            <div
                                                key={opt.id}
                                                onClick={() => handleSelectOption(q.questionId, opt.id)}
                                                className={`p-3.5 rounded-xl border text-sm cursor-pointer transition-all flex items-center gap-3 ${
                                                    checked
                                                        ? "border-primary bg-primary/5 text-primary font-semibold shadow-sm"
                                                        : "border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50"
                                                }`}
                                            >
                                                <div
                                                    className={`w-5 h-5 rounded-full border flex items-center justify-center text-xs shrink-0 font-bold ${
                                                        checked
                                                            ? "border-primary bg-primary text-white"
                                                            : "border-slate-300 text-slate-500"
                                                    }`}
                                                >
                                                    {opt.label}
                                                </div>
                                                <span className="leading-relaxed">{opt.content}</span>
                                            </div>
                                        );
                                    })}
                                </div>
                            </CardContent>
                        </Card>
                    );
                })}
            </div>

            {/* Bottom Submit Action */}
            <div className="pt-4 flex justify-end">
                <Button
                    size="lg"
                    variant="default"
                    disabled={isSubmitting}
                    onClick={() => {
                        if (window.confirm("Bạn có chắc chắn muốn nộp bài kiểm tra ngay bây giờ không?")) {
                            void doSubmit(false);
                        }
                    }}
                    className="gap-2 bg-emerald-600 hover:bg-emerald-700"
                >
                    <Send className="w-4 h-4" />
                    {isSubmitting ? "Đang gửi bài..." : "Hoàn thành & Nộp bài thi"}
                </Button>
            </div>
        </div>
    );
};

export default ClassTestTakePage;
