import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArrowLeft, Send } from "lucide-react";
import quizService from "../services/quizService";
import { OptionLetter, QuizExam } from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const QuizTakePage: React.FC = () => {
    const { quizId } = useParams<{ quizId: string }>();
    const navigate = useNavigate();
    const id = Number(quizId);

    const [quiz, setQuiz] = useState<QuizExam | null>(null);
    const [selectedAnswers, setSelectedAnswers] = useState<Record<number, OptionLetter>>({});
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchQuiz = async () => {
            try {
                setIsLoading(true);
                const data = await quizService.getQuizExam(id);
                setQuiz(data);
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải đề thi.");
            } finally {
                setIsLoading(false);
            }
        };

        if (!isNaN(id)) {
            fetchQuiz();
        }
    }, [id]);

    const handleSelectOption = (questionId: number, option: OptionLetter) => {
        setSelectedAnswers((prev) => ({
            ...prev,
            [questionId]: option
        }));
    };

    const answeredCount = Object.keys(selectedAnswers).length;
    const totalQuestions = quiz?.questions.length || 0;
    const progressPercent = totalQuestions > 0 ? Math.round((answeredCount * 100) / totalQuestions) : 0;

    const handleSubmitQuiz = async () => {
        if (!quiz) return;

        if (answeredCount < totalQuestions) {
            const unanswered = totalQuestions - answeredCount;
            const confirm = window.confirm(
                `Bạn còn ${unanswered} câu chưa trả lời. Bạn có chắc chắn muốn nộp bài ngay bây giờ không?`
            );
            if (!confirm) return;
        }

        try {
            setIsSubmitting(true);
            const answersPayload = quiz.questions.map((q) => ({
                questionId: q.id,
                selectedOption: selectedAnswers[q.id]
            }));

            const result = await quizService.submitQuiz(id, { answers: answersPayload });
            navigate(`/attempts/${result.attemptId}`);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Nộp bài thi thất bại.");
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang chuẩn bị đề thi..." className="py-20" />;
    }

    if (error || !quiz) {
        return (
            <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700">
                <p className="font-semibold">Lỗi</p>
                <p className="text-sm mt-1">{error || "Đề thi không tồn tại."}</p>
                <button onClick={() => navigate(-1)} className="mt-4 text-sm font-semibold underline">
                    Quay lại
                </button>
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto space-y-6 pb-16">
            {/* Top Toolbar */}
            <div className="flex items-center justify-between">
                <button
                    onClick={() => navigate(-1)}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Thoát bài thi</span>
                </button>
            </div>

            {/* Quiz Banner & Progress Sticky Card */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm sticky top-20 z-10 space-y-4">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                                Mức độ: {quiz.difficulty}
                            </span>
                            <span className="text-xs text-slate-400">
                                {quiz.questionCount} câu hỏi trắc nghiệm
                            </span>
                        </div>
                        <h1 className="text-lg md:text-xl font-bold text-slate-800 mt-1">{quiz.title}</h1>
                    </div>

                    <button
                        onClick={handleSubmitQuiz}
                        disabled={isSubmitting}
                        className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold text-sm shadow-sm transition disabled:opacity-50"
                    >
                        {isSubmitting ? (
                            <>
                                <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                <span>Đang chấm điểm...</span>
                            </>
                        ) : (
                            <>
                                <Send className="w-4 h-4" />
                                <span>Nộp bài thi</span>
                            </>
                        )}
                    </button>
                </div>

                {/* Progress Bar */}
                <div className="space-y-1">
                    <div className="flex justify-between text-xs font-medium text-slate-500">
                        <span>Tiến độ hoàn thành: {answeredCount}/{totalQuestions} câu</span>
                        <span>{progressPercent}%</span>
                    </div>
                    <div className="w-full h-2 bg-slate-100 rounded-full overflow-hidden">
                        <div
                            className="h-full bg-emerald-500 transition-all duration-300 rounded-full"
                            style={{ width: `${progressPercent}%` }}
                        />
                    </div>
                </div>
            </div>

            {/* Questions List */}
            <div className="space-y-6">
                {quiz.questions.map((q, qIndex) => {
                    const currentSelected = selectedAnswers[q.id];

                    const options: { key: OptionLetter; text: string }[] = [
                        { key: "A", text: q.optionA },
                        { key: "B", text: q.optionB },
                        { key: "C", text: q.optionC },
                        { key: "D", text: q.optionD }
                    ];

                    return (
                        <div
                            key={q.id}
                            className="bg-white rounded-2xl border border-slate-200/90 shadow-sm p-6 space-y-4"
                        >
                            <div className="flex items-start gap-3">
                                <span className="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-700 font-bold text-xs flex items-center justify-center shrink-0 mt-0.5">
                                    {qIndex + 1}
                                </span>
                                <h3 className="text-sm md:text-base font-semibold text-slate-800 leading-snug">
                                    {q.questionText}
                                </h3>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
                                {options.map((opt) => {
                                    const isChosen = currentSelected === opt.key;

                                    return (
                                        <div
                                            key={opt.key}
                                            onClick={() => handleSelectOption(q.id, opt.key)}
                                            className={`p-3.5 rounded-xl border text-xs md:text-sm cursor-pointer transition flex items-center gap-3 ${
                                                isChosen
                                                    ? "border-indigo-600 bg-indigo-50/70 text-indigo-900 font-medium ring-2 ring-indigo-500/20"
                                                    : "border-slate-200 hover:border-slate-300 bg-slate-50/40 text-slate-700 hover:bg-slate-50"
                                            }`}
                                        >
                                            <div
                                                className={`w-6 h-6 rounded-lg font-bold text-xs flex items-center justify-center shrink-0 border ${
                                                    isChosen
                                                        ? "bg-indigo-600 text-white border-indigo-600"
                                                        : "bg-white text-slate-500 border-slate-300"
                                                }`}
                                            >
                                                {opt.key}
                                            </div>
                                            <span className="flex-1 leading-normal">{opt.text}</span>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Bottom Submit Toolbar */}
            <div className="pt-4 flex justify-end">
                <button
                    onClick={handleSubmitQuiz}
                    disabled={isSubmitting}
                    className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white font-semibold text-sm shadow-md shadow-emerald-600/20 transition disabled:opacity-50"
                >
                    <Send className="w-4 h-4" />
                    <span>Hoàn tất & Nộp bài thi</span>
                </button>
            </div>
        </div>
    );
};

export default QuizTakePage;
