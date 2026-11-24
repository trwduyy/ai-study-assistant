import React, { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft,
    Award,
    CheckCircle2,
    RotateCcw,
    Sparkles,
    XCircle
} from "lucide-react";
import quizService from "../services/quizService";
import { AttemptDetail, OptionLetter } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const QuizResultPage: React.FC = () => {
    const { attemptId } = useParams<{ attemptId: string }>();
    const navigate = useNavigate();
    const id = Number(attemptId);

    const [attempt, setAttempt] = useState<AttemptDetail | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchAttempt = async () => {
            try {
                setIsLoading(true);
                const data = await quizService.getAttemptDetail(id);
                setAttempt(data);
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải kết quả bài thi.");
            } finally {
                setIsLoading(false);
            }
        };

        if (!isNaN(id)) {
            fetchAttempt();
        }
    }, [id]);

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang tải kết quả bài làm..." className="py-20" />;
    }

    if (error || !attempt) {
        return (
            <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700">
                <p className="font-semibold">Lỗi</p>
                <p className="text-sm mt-1">{error || "Kết quả không tồn tại."}</p>
                <Link to="/courses" className="mt-4 inline-block text-sm font-semibold underline">
                    Quay lại danh sách khóa học
                </Link>
            </div>
        );
    }

    const accuracy = attempt.totalQuestions > 0
        ? Math.round((attempt.correctCount * 100) / attempt.totalQuestions)
        : 0;

    const isPassed = attempt.score >= 50;

    return (
        <div className="max-w-4xl mx-auto space-y-8 pb-16">
            {/* Top Back Action */}
            <div className="flex items-center justify-between">
                <button
                    onClick={() => navigate("/history")}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Xem lịch sử làm bài</span>
                </button>
            </div>

            {/* Score Summary Card */}
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-8 flex flex-col md:flex-row items-center justify-between gap-6">
                <div className="flex items-center gap-6">
                    {/* Circular Score Badge */}
                    <div
                        className={`w-24 h-24 rounded-full flex flex-col items-center justify-center font-bold border-4 shrink-0 shadow-inner ${
                            isPassed
                                ? "bg-emerald-50 border-emerald-500 text-emerald-700"
                                : "bg-rose-50 border-rose-500 text-rose-700"
                        }`}
                    >
                        <span className="text-2xl leading-none">{attempt.score}</span>
                        <span className="text-[10px] text-slate-400 mt-1 uppercase tracking-wider font-semibold">
                            Điểm số
                        </span>
                    </div>

                    <div>
                        <div className="flex items-center gap-2">
                            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-100">
                                Kết quả đánh giá
                            </span>
                        </div>
                        <h1 className="text-xl md:text-2xl font-bold text-slate-800 mt-1">
                            {attempt.quizTitle}
                        </h1>
                        <p className="text-xs text-slate-400 mt-1">
                            Nộp bài lúc: {new Date(attempt.submittedAt).toLocaleString("vi-VN")}
                        </p>
                    </div>
                </div>

                {/* Score Stats */}
                <div className="flex items-center gap-4 self-stretch md:self-auto justify-around border-t md:border-t-0 md:border-l border-slate-100 pt-4 md:pt-0 md:pl-6">
                    <div className="text-center">
                        <span className="text-xs font-medium text-slate-400">Số câu đúng</span>
                        <p className="text-xl font-bold text-slate-800">
                            {attempt.correctCount}/{attempt.totalQuestions}
                        </p>
                    </div>
                    <div className="text-center">
                        <span className="text-xs font-medium text-slate-400">Độ chính xác</span>
                        <p className="text-xl font-bold text-indigo-600">{accuracy}%</p>
                    </div>
                    <Link
                        to={`/quizzes/${attempt.quizId}/take`}
                        className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs transition shadow-sm ml-2"
                    >
                        <RotateCcw className="w-3.5 h-3.5" />
                        <span>Làm lại</span>
                    </Link>
                </div>
            </div>

            {/* Questions Detailed Review List */}
            <div className="space-y-6">
                <div className="flex items-center justify-between">
                    <h3 className="font-bold text-base text-slate-800 flex items-center gap-2">
                        <Award className="w-5 h-5 text-indigo-600" />
                        <span>Chi tiết từng câu hỏi & Lời giải thích AI</span>
                    </h3>
                </div>

                {attempt.answers.map((ans, idx) => {
                    const isCorrect = ans.isCorrect;

                    const options: { key: OptionLetter; text: string }[] = [
                        { key: "A", text: ans.optionA },
                        { key: "B", text: ans.optionB },
                        { key: "C", text: ans.optionC },
                        { key: "D", text: ans.optionD }
                    ];

                    return (
                        <div
                            key={ans.questionId}
                            className={`bg-white rounded-2xl border p-6 space-y-4 shadow-sm transition ${
                                isCorrect ? "border-emerald-200" : "border-rose-200"
                            }`}
                        >
                            {/* Question Header & Correctness status */}
                            <div className="flex items-start justify-between gap-3">
                                <div className="flex items-start gap-3">
                                    <span
                                        className={`w-7 h-7 rounded-lg font-bold text-xs flex items-center justify-center shrink-0 mt-0.5 ${
                                            isCorrect
                                                ? "bg-emerald-100 text-emerald-800"
                                                : "bg-rose-100 text-rose-800"
                                        }`}
                                    >
                                        {idx + 1}
                                    </span>
                                    <h4 className="text-sm md:text-base font-semibold text-slate-800 leading-snug">
                                        {ans.questionText}
                                    </h4>
                                </div>

                                <div>
                                    {isCorrect ? (
                                        <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                                            <CheckCircle2 className="w-3.5 h-3.5" /> Đúng
                                        </span>
                                    ) : (
                                        <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-200">
                                            <XCircle className="w-3.5 h-3.5" /> Sai
                                        </span>
                                    )}
                                </div>
                            </div>

                            {/* Options Grid */}
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1">
                                {options.map((opt) => {
                                    const isUserChoice = ans.selectedOption === opt.key;
                                    const isCorrectAnswer = ans.correctOption === opt.key;

                                    let badgeStyle = "border-slate-200 bg-slate-50/50 text-slate-700";
                                    if (isCorrectAnswer) {
                                        badgeStyle = "border-emerald-500 bg-emerald-50 text-emerald-900 font-medium ring-1 ring-emerald-500/30";
                                    } else if (isUserChoice && !isCorrect) {
                                        badgeStyle = "border-rose-400 bg-rose-50 text-rose-900 font-medium";
                                    }

                                    return (
                                        <div
                                            key={opt.key}
                                            className={`p-3 rounded-xl border text-xs md:text-sm flex items-center gap-2.5 ${badgeStyle}`}
                                        >
                                            <div
                                                className={`w-5 h-5 rounded-md font-bold text-[11px] flex items-center justify-center shrink-0 border ${
                                                    isCorrectAnswer
                                                        ? "bg-emerald-600 text-white border-emerald-600"
                                                        : isUserChoice && !isCorrect
                                                        ? "bg-rose-600 text-white border-rose-600"
                                                        : "bg-white text-slate-500 border-slate-300"
                                                }`}
                                            >
                                                {opt.key}
                                            </div>
                                            <span className="flex-1">{opt.text}</span>
                                            {isCorrectAnswer && (
                                                <span className="text-[10px] font-bold text-emerald-700 bg-emerald-100 px-1.5 py-0.5 rounded">
                                                    Đáp án đúng
                                                </span>
                                            )}
                                            {isUserChoice && !isCorrect && (
                                                <span className="text-[10px] font-bold text-rose-700 bg-rose-100 px-1.5 py-0.5 rounded">
                                                    Bạn đã chọn
                                                </span>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>

                            {/* AI Explanation Box */}
                            {ans.explanation && (
                                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/80 text-xs text-slate-700 space-y-1">
                                    <div className="flex items-center gap-1.5 font-bold text-indigo-700 text-xs">
                                        <Sparkles className="w-3.5 h-3.5" />
                                        <span>Giải thích chi tiết từ AI:</span>
                                    </div>
                                    <p className="leading-relaxed pl-5 text-slate-600">
                                        {ans.explanation}
                                    </p>
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default QuizResultPage;
