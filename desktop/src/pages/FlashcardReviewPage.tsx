import React, { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft,
    CheckCircle2,
    Clock,
    HelpCircle,
    Layers,
    RotateCcw,
    Sparkles,
    Trophy
} from "lucide-react";
import flashcardService from "../services/flashcardService";
import { FlashcardItem, FlashcardRating, FlashcardReviewQueue } from "../types";
import { Button } from "../components/ui/button";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { toast } from "sonner";

export const FlashcardReviewPage: React.FC = () => {
    const { deckId } = useParams<{ deckId: string }>();
    const navigate = useNavigate();
    const id = Number(deckId);

    const [queue, setQueue] = useState<FlashcardReviewQueue | null>(null);
    const [currentIndex, setCurrentIndex] = useState<number>(0);
    const [isFlipped, setIsFlipped] = useState<boolean>(false);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
    const [error, setError] = useState<string | null>(null);
    const [isFinished, setIsFinished] = useState<boolean>(false);

    useEffect(() => {
        const fetchReviewQueue = async () => {
            try {
                setIsLoading(true);
                const data = await flashcardService.getDueCards(id);
                setQueue(data);
                if (data.cards.length === 0) {
                    setIsFinished(true);
                }
                setError(null);
            } catch (err: unknown) {
                setError(err instanceof Error ? err.message : "Không thể tải danh sách thẻ cần ôn.");
            } finally {
                setIsLoading(false);
            }
        };

        if (!isNaN(id)) {
            void fetchReviewQueue();
        }
    }, [id]);

    const handleFlip = () => {
        setIsFlipped((prev) => !prev);
    };

    const handleRating = async (rating: FlashcardRating) => {
        if (!queue || isSubmitting) return;

        const currentCard = queue.cards[currentIndex];
        if (!currentCard) return;

        try {
            setIsSubmitting(true);
            await flashcardService.submitReview(currentCard.id, { rating });

            if (currentIndex + 1 < queue.cards.length) {
                setCurrentIndex((prev) => prev + 1);
                setIsFlipped(false);
            } else {
                setIsFinished(true);
                toast.success("Tuyệt vời! Bạn đã hoàn thành buổi ôn tập hôm nay.");
            }
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Lỗi lưu kết quả ôn tập.");
        } finally {
            setIsSubmitting(false);
        }
    };

    // Keyboard navigation: Space to flip, 1-4 to rate
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (isFinished || isSubmitting) return;

            if (e.code === "Space") {
                e.preventDefault();
                handleFlip();
            } else if (isFlipped) {
                if (e.key === "1") {
                    void handleRating("FORGOT");
                } else if (e.key === "2") {
                    void handleRating("HARD");
                } else if (e.key === "3") {
                    void handleRating("OK");
                } else if (e.key === "4") {
                    void handleRating("EASY");
                }
            }
        };

        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [isFlipped, isFinished, isSubmitting, currentIndex, queue]);

    if (isLoading) {
        return <LoadingState text="Đang chuẩn bị các thẻ ghi nhớ cần ôn hôm nay..." />;
    }

    if (error) {
        return (
            <ErrorBanner
                title="Không thể ôn tập"
                message={error}
                onRetry={() => window.location.reload()}
            />
        );
    }

    if (isFinished || !queue || queue.cards.length === 0) {
        return (
            <div className="max-w-md mx-auto text-center py-16 space-y-5 animate-in fade-in">
                <div className="w-20 h-20 rounded-full bg-emerald-50 text-emerald-600 flex items-center justify-center mx-auto border-2 border-emerald-200 shadow-sm">
                    <Trophy className="w-10 h-10" />
                </div>
                <div className="space-y-2">
                    <h2 className="text-xl font-bold text-slate-900">
                        Hoàn thành mục tiêu ôn tập!
                    </h2>
                    <p className="text-sm text-slate-500">
                        Bạn đã ôn tập xong toàn bộ thẻ đến hạn hôm nay của bộ thẻ "{queue?.deckTitle}".
                    </p>
                </div>
                <Button onClick={() => navigate("/flashcards")} className="gap-2">
                    <ArrowLeft className="w-4 h-4" />
                    <span>Quay lại danh sách bộ thẻ</span>
                </Button>
            </div>
        );
    }

    const currentCard = queue.cards[currentIndex];
    const totalCards = queue.cards.length;
    const progressPercent = Math.round(((currentIndex + 1) * 100) / totalCards);

    return (
        <div className="max-w-2xl mx-auto space-y-6 pb-20">
            {/* Top Toolbar */}
            <div className="flex items-center justify-between">
                <button
                    onClick={() => navigate("/flashcards")}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Dừng buổi ôn tập</span>
                </button>
                <div className="text-xs text-slate-500 font-semibold">
                    {queue.deckTitle}
                </div>
            </div>

            {/* Progress Header */}
            <div className="space-y-1.5">
                <div className="flex items-center justify-between text-xs font-semibold text-slate-600">
                    <span>
                        Thẻ {currentIndex + 1} / {totalCards}
                    </span>
                    <span>{progressPercent}%</span>
                </div>
                <div className="w-full h-2 rounded-full bg-slate-100 overflow-hidden">
                    <div
                        className="h-full bg-emerald-600 transition-all duration-300"
                        style={{ width: `${progressPercent}%` }}
                    />
                </div>
            </div>

            {/* 3D Interactive Flip Card */}
            <div
                onClick={handleFlip}
                className="w-full min-h-[320px] rounded-2xl border-2 border-slate-200 bg-white shadow-elevated p-8 flex flex-col justify-between cursor-pointer hover:border-primary/50 transition-all select-none"
            >
                {/* Card Top Label */}
                <div className="flex items-center justify-between text-xs text-slate-400">
                    <Badge variant={isFlipped ? "success" : "default"} className="text-[11px]">
                        {isFlipped ? "Mặt sau: Câu trả lời" : "Mặt trước: Khái niệm / Câu hỏi"}
                    </Badge>
                    <span className="text-[11px]">
                        Bấm thẻ hoặc nhấn Phím Cách (Space) để lật
                    </span>
                </div>

                {/* Card Main Body */}
                <div className="my-auto py-8 text-center">
                    <p className="text-xl md:text-2xl font-bold text-slate-900 leading-relaxed font-sans">
                        {isFlipped ? currentCard.back : currentCard.front}
                    </p>
                </div>

                {/* Card Bottom Hint */}
                <div className="text-center text-xs text-slate-400 pt-2 border-t border-slate-100">
                    {isFlipped ? (
                        <span className="text-primary font-medium">
                            Đánh giá mức độ ghi nhớ của bạn bên dưới
                        </span>
                    ) : (
                        <span>Nhấn để xem đáp án</span>
                    )}
                </div>
            </div>

            {/* SM-2 Rating Buttons (Only shown when flipped) */}
            {isFlipped ? (
                <div className="space-y-2 animate-in fade-in">
                    <p className="text-xs font-semibold text-center text-slate-500 uppercase tracking-wider">
                        Đánh giá mức nhớ (Phím tắt: 1 - 4)
                    </p>
                    <div className="grid grid-cols-4 gap-3">
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => handleRating("FORGOT")}
                            disabled={isSubmitting}
                            className="flex flex-col h-16 border-rose-200 hover:bg-rose-50 text-rose-700"
                        >
                            <span className="text-xs font-bold">1: Quên</span>
                            <span className="text-[10px] text-slate-400 mt-0.5">Ôn lại ngay</span>
                        </Button>

                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => handleRating("HARD")}
                            disabled={isSubmitting}
                            className="flex flex-col h-16 border-amber-200 hover:bg-amber-50 text-amber-700"
                        >
                            <span className="text-xs font-bold">2: Khó</span>
                            <span className="text-[10px] text-slate-400 mt-0.5">+1 ngày</span>
                        </Button>

                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => handleRating("OK")}
                            disabled={isSubmitting}
                            className="flex flex-col h-16 border-blue-200 hover:bg-blue-50 text-blue-700"
                        >
                            <span className="text-xs font-bold">3: Tạm</span>
                            <span className="text-[10px] text-slate-400 mt-0.5">+3 ngày</span>
                        </Button>

                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => handleRating("EASY")}
                            disabled={isSubmitting}
                            className="flex flex-col h-16 border-emerald-200 hover:bg-emerald-50 text-emerald-700"
                        >
                            <span className="text-xs font-bold">4: Dễ</span>
                            <span className="text-[10px] text-slate-400 mt-0.5">+7 ngày</span>
                        </Button>
                    </div>
                </div>
            ) : (
                <div className="text-center">
                    <Button onClick={handleFlip} className="gap-2 px-8">
                        <RotateCcw className="w-4 h-4" />
                        <span>Lật thẻ xem đáp án (Space)</span>
                    </Button>
                </div>
            )}
        </div>
    );
};

export default FlashcardReviewPage;
