import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
    ArrowLeft,
    BookOpen,
    Calendar,
    CheckCircle2,
    Clock,
    Edit2,
    Eye,
    Layers,
    Play,
    Plus,
    RotateCcw,
    Sparkles,
    Trash2,
    X
} from "lucide-react";
import flashcardService from "../services/flashcardService";
import learningService from "../services/learningService";
import {
    Course,
    FlashcardDeckDetail,
    FlashcardDeckSummary,
    FlashcardItem,
    Material
} from "../types";
import { useAiJob } from "../hooks/useAiJob";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Card, CardContent } from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { EmptyState } from "../components/common/EmptyState";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { toast } from "sonner";

export const FlashcardsPage: React.FC = () => {
    const navigate = useNavigate();

    const [decks, setDecks] = useState<FlashcardDeckSummary[]>([]);
    const [courses, setCourses] = useState<Course[]>([]);
    const [selectedCourseId, setSelectedCourseId] = useState<number | undefined>(undefined);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Manual Deck Modal
    const [isManualModalOpen, setIsManualModalOpen] = useState<boolean>(false);
    const [manualCourseId, setManualCourseId] = useState<number>(0);
    const [manualDeckTitle, setManualDeckTitle] = useState<string>("");
    const [isManualSubmitting, setIsManualSubmitting] = useState<boolean>(false);

    // AI Flashcard Modal
    const [isAiModalOpen, setIsAiModalOpen] = useState<boolean>(false);
    const [aiCourseId, setAiCourseId] = useState<number>(0);
    const [aiDeckTitle, setAiDeckTitle] = useState<string>("");
    const [aiCardCount, setAiCardCount] = useState<number>(10);
    const [aiMaterials, setAiMaterials] = useState<Material[]>([]);
    const [aiSelectedMaterialIds, setAiSelectedMaterialIds] = useState<number[]>([]);

    // Deck Detail & Add Card Drawer
    const [selectedDeckDetail, setSelectedDeckDetail] = useState<FlashcardDeckDetail | null>(null);
    const [newCardFront, setNewCardFront] = useState<string>("");
    const [newCardBack, setNewCardBack] = useState<string>("");
    const [isAddingCard, setIsAddingCard] = useState<boolean>(false);

    // AI Job Hook
    const {
        jobId: aiJobId,
        isLoading: isAiJobLoading,
        progressText: aiJobProgressText,
        startJob: startAiJob,
        cancel: cancelAiJob
    } = useAiJob(null, {
        onCompleted: () => {
            toast.success("Tạo bộ Flashcard AI thành công!");
            setIsAiModalOpen(false);
            void fetchDecks();
        },
        onError: (err) => toast.error(err)
    });

    const fetchDecks = async (courseId?: number) => {
        try {
            setIsLoading(true);
            const res = await flashcardService.listDecks(courseId);
            setDecks(res.items);
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải danh sách bộ thẻ.");
        } finally {
            setIsLoading(false);
        }
    };

    const fetchCourses = async () => {
        try {
            const res = await learningService.getCourses(0, 50);
            setCourses(res.items);
            if (res.items.length > 0) {
                setManualCourseId(res.items[0].id);
                setAiCourseId(res.items[0].id);
            }
        } catch {
            // Ignore background error
        }
    };

    useEffect(() => {
        void fetchDecks(selectedCourseId);
        void fetchCourses();
    }, [selectedCourseId]);

    // Load materials when AI Course changes
    useEffect(() => {
        if (!aiCourseId) return;

        const loadMaterialsForCourse = async () => {
            try {
                const res = await learningService.getMaterials(aiCourseId);
                const extracted = res.items.filter((m) => m.status === "EXTRACTED");
                setAiMaterials(extracted);
                setAiSelectedMaterialIds(extracted.map((m) => m.id));
            } catch {
                setAiMaterials([]);
            }
        };

        void loadMaterialsForCourse();
    }, [aiCourseId]);

    const handleCreateManualDeck = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!manualDeckTitle.trim() || !manualCourseId) return;

        try {
            setIsManualSubmitting(true);
            const created = await flashcardService.createDeck({
                courseId: manualCourseId,
                title: manualDeckTitle.trim()
            });
            setDecks((prev) => [created, ...prev]);
            toast.success("Tạo bộ thẻ thành công!");
            setIsManualModalOpen(false);
            setManualDeckTitle("");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo bộ thẻ thất bại.");
        } finally {
            setIsManualSubmitting(false);
        }
    };

    const handleStartAiGeneration = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!aiDeckTitle.trim() || !aiCourseId) {
            toast.error("Vui lòng điền tiêu đề bộ thẻ.");
            return;
        }
        if (aiSelectedMaterialIds.length === 0) {
            toast.error("Vui lòng chọn ít nhất một tài liệu nguồn.");
            return;
        }

        try {
            const accepted = await flashcardService.generateAiFlashcards({
                courseId: aiCourseId,
                materialIds: aiSelectedMaterialIds,
                deckTitle: aiDeckTitle.trim(),
                cardCount: aiCardCount
            });
            startAiJob(accepted.jobId);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Yêu cầu tạo thẻ AI thất bại.");
        }
    };

    const handleDeleteDeck = async (deck: FlashcardDeckSummary) => {
        if (!window.confirm(`Bạn có chắc muốn xóa bộ thẻ "${deck.title}" và toàn bộ thẻ bên trong không?`)) {
            return;
        }

        try {
            await flashcardService.deleteDeck(deck.id);
            setDecks((prev) => prev.filter((d) => d.id !== deck.id));
            toast.success("Đã xóa bộ thẻ.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa thất bại.");
        }
    };

    const handleOpenDeckCards = async (deckId: number) => {
        try {
            const detail = await flashcardService.getDeckDetail(deckId);
            setSelectedDeckDetail(detail);
            setNewCardFront("");
            setNewCardBack("");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không thể tải danh sách thẻ.");
        }
    };

    const handleAddCard = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedDeckDetail || !newCardFront.trim() || !newCardBack.trim()) return;

        try {
            setIsAddingCard(true);
            const card = await flashcardService.addCard(selectedDeckDetail.id, {
                front: newCardFront.trim(),
                back: newCardBack.trim()
            });

            setSelectedDeckDetail((prev) =>
                prev
                    ? {
                          ...prev,
                          totalCards: prev.totalCards + 1,
                          dueCardsCount: prev.dueCardsCount + 1,
                          cards: [card, ...prev.cards]
                      }
                    : null
            );

            setDecks((prev) =>
                prev.map((d) =>
                    d.id === selectedDeckDetail.id
                        ? { ...d, totalCards: d.totalCards + 1, dueCardsCount: d.dueCardsCount + 1 }
                        : d
                )
            );

            toast.success("Thêm thẻ mới thành công!");
            setNewCardFront("");
            setNewCardBack("");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Thêm thẻ thất bại.");
        } finally {
            setIsAddingCard(false);
        }
    };

    return (
        <div className="space-y-6 pb-12">
            {/* Header Toolbar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                        <Layers className="w-6 h-6 text-emerald-600" />
                        <span>Bộ thẻ Flashcards & Ôn tập SM-2</span>
                    </h1>
                    <p className="text-sm text-slate-500 mt-1">
                        Ghi nhớ dài hạn với thuật toán lặp lại ngắt quãng (Spaced Repetition)
                    </p>
                </div>

                <div className="flex items-center gap-3">
                    <Button
                        variant="secondary"
                        onClick={() => setIsAiModalOpen(true)}
                        className="gap-2"
                    >
                        <Sparkles className="w-4 h-4 text-emerald-300" />
                        <span>Tạo thẻ bằng AI</span>
                    </Button>
                    <Button
                        onClick={() => setIsManualModalOpen(true)}
                        className="gap-2"
                    >
                        <Plus className="w-4 h-4" />
                        <span>Tạo bộ thẻ mới</span>
                    </Button>
                </div>
            </div>

            {/* Course Filter Bar */}
            <div className="flex items-center gap-3">
                <span className="text-xs font-semibold uppercase text-slate-500">Môn học:</span>
                <select
                    value={selectedCourseId || ""}
                    onChange={(e) => setSelectedCourseId(e.target.value ? Number(e.target.value) : undefined)}
                    className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-primary"
                >
                    <option value="">-- Tất cả môn học --</option>
                    {courses.map((c) => (
                        <option key={c.id} value={c.id}>
                            {c.name}
                        </option>
                    ))}
                </select>
            </div>

            {error && (
                <ErrorBanner
                    title="Lỗi tải bộ thẻ"
                    message={error}
                    onRetry={() => fetchDecks(selectedCourseId)}
                />
            )}

            {isLoading ? (
                <LoadingState text="Đang tải danh sách bộ thẻ flashcards..." />
            ) : decks.length === 0 ? (
                <EmptyState
                    icon={<Layers className="w-8 h-8 text-slate-400" />}
                    title="Chưa có bộ thẻ Flashcard nào"
                    description="Tạo bộ thẻ từ tài liệu bài giảng hoặc tự thêm thẻ để bắt đầu ôn tập thông minh."
                    action={
                        <Button onClick={() => setIsAiModalOpen(true)} className="gap-2">
                            <Sparkles className="w-4 h-4 text-emerald-300" />
                            <span>Tạo bộ thẻ bằng AI</span>
                        </Button>
                    }
                />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {decks.map((deck) => {
                        const hasDueCards = deck.dueCardsCount > 0;

                        return (
                            <Card
                                key={deck.id}
                                className="hover:border-primary/40 hover:shadow-elevated transition-all flex flex-col justify-between"
                            >
                                <CardContent className="p-6 space-y-4">
                                    <div className="flex items-start justify-between gap-3">
                                        <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center font-bold shrink-0 border border-emerald-100">
                                            <Layers className="w-5 h-5" />
                                        </div>

                                        <div className="flex items-center gap-1">
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleOpenDeckCards(deck.id)}
                                                className="h-8 w-8 p-0 text-slate-500 hover:text-slate-900"
                                                title="Xem danh sách thẻ"
                                            >
                                                <Eye className="w-4 h-4" />
                                            </Button>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleDeleteDeck(deck)}
                                                className="h-8 w-8 p-0 text-rose-500 hover:text-rose-700"
                                                title="Xóa bộ thẻ"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </Button>
                                        </div>
                                    </div>

                                    <div>
                                        <h3 className="font-bold text-slate-900 text-base line-clamp-1">
                                            {deck.title}
                                        </h3>
                                        <p className="text-xs text-slate-500 mt-0.5">
                                            Môn: {deck.courseName || "Chung"}
                                        </p>
                                    </div>

                                    <div className="p-3 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between text-xs">
                                        <div className="space-y-0.5">
                                            <span className="text-slate-400 font-medium">Tổng số thẻ:</span>
                                            <p className="font-bold text-slate-900">{deck.totalCards} thẻ</p>
                                        </div>
                                        <div className="space-y-0.5 text-right">
                                            <span className="text-slate-400 font-medium">Cần ôn hôm nay:</span>
                                            <p className={`font-bold ${hasDueCards ? "text-amber-600" : "text-emerald-600"}`}>
                                                {deck.dueCardsCount} thẻ
                                            </p>
                                        </div>
                                    </div>

                                    <div className="pt-2 flex items-center gap-2">
                                        <Button
                                            onClick={() => navigate(`/flashcards/${deck.id}/review`)}
                                            disabled={!hasDueCards}
                                            className="w-full gap-2 text-xs h-9"
                                        >
                                            <Play className="w-3.5 h-3.5 fill-current" />
                                            <span>{hasDueCards ? "Ôn tập ngay" : "Đã ôn xong hôm nay"}</span>
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
                </div>
            )}

            {/* Modal: Create Manual Deck */}
            {isManualModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-sm bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900">Tạo bộ thẻ mới</h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsManualModalOpen(false)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleCreateManualDeck} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chọn môn học <span className="text-rose-500">*</span>
                                </label>
                                <select
                                    value={manualCourseId}
                                    onChange={(e) => setManualCourseId(Number(e.target.value))}
                                    required
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                >
                                    {courses.map((c) => (
                                        <option key={c.id} value={c.id}>
                                            {c.name}
                                        </option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tiêu đề bộ thẻ <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Giao thức mạng, Thuật ngữ bảo mật..."
                                    value={manualDeckTitle}
                                    onChange={(e) => setManualDeckTitle(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div className="flex items-center justify-end gap-2 pt-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsManualModalOpen(false)}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit" isLoading={isManualSubmitting}>
                                    Tạo bộ thẻ
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: AI Flashcard Generator */}
            {isAiModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-lg bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                                <Sparkles className="w-5 h-5 text-emerald-600" />
                                <span>Sinh bộ thẻ Flashcard bằng AI</span>
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => !isAiJobLoading && setIsAiModalOpen(false)}
                                disabled={isAiJobLoading}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleStartAiGeneration} className="p-6 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chọn môn học <span className="text-rose-500">*</span>
                                </label>
                                <select
                                    value={aiCourseId}
                                    onChange={(e) => setAiCourseId(Number(e.target.value))}
                                    disabled={isAiJobLoading}
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                >
                                    {courses.map((c) => (
                                        <option key={c.id} value={c.id}>
                                            {c.name}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tiêu đề bộ thẻ <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Flashcards Tầng giao vận TCP/UDP..."
                                    value={aiDeckTitle}
                                    onChange={(e) => setAiDeckTitle(e.target.value)}
                                    required
                                    disabled={isAiJobLoading}
                                />
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Số lượng thẻ cần sinh
                                </label>
                                <select
                                    value={aiCardCount}
                                    onChange={(e) => setAiCardCount(Number(e.target.value))}
                                    disabled={isAiJobLoading}
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary"
                                >
                                    <option value={5}>5 thẻ</option>
                                    <option value={10}>10 thẻ</option>
                                    <option value={15}>15 thẻ</option>
                                    <option value={20}>20 thẻ</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Chọn tài liệu nguồn <span className="text-rose-500">*</span>
                                </label>
                                <div className="max-h-36 overflow-y-auto border border-slate-200 rounded-lg p-2 space-y-1.5 bg-slate-50/50">
                                    {aiMaterials.length === 0 ? (
                                        <p className="text-xs text-slate-400 p-2 text-center">
                                            Môn học này chưa có tài liệu nào ở trạng thái "Sẵn sàng".
                                        </p>
                                    ) : (
                                        aiMaterials.map((mat) => {
                                            const checked = aiSelectedMaterialIds.includes(mat.id);
                                            return (
                                                <label
                                                    key={mat.id}
                                                    className="flex items-center gap-2 p-1.5 rounded hover:bg-slate-100 cursor-pointer text-xs text-slate-800"
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={checked}
                                                        disabled={isAiJobLoading}
                                                        onChange={(e) => {
                                                            if (e.target.checked) {
                                                                setAiSelectedMaterialIds((prev) => [...prev, mat.id]);
                                                            } else {
                                                                setAiSelectedMaterialIds((prev) =>
                                                                    prev.filter((mid) => mid !== mat.id)
                                                                );
                                                            }
                                                        }}
                                                        className="rounded border-slate-300 text-primary focus:ring-primary"
                                                    />
                                                    <span className="truncate">{mat.fileName}</span>
                                                </label>
                                            );
                                        })
                                    )}
                                </div>
                            </div>

                            {isAiJobLoading && (
                                <div className="p-3.5 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-between text-xs text-slate-800">
                                    <div className="flex items-center gap-2">
                                        <Sparkles className="w-4 h-4 text-emerald-600 animate-spin" />
                                        <span>{aiJobProgressText}</span>
                                    </div>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={cancelAiJob}
                                        className="h-7 text-xs text-rose-600 border-rose-200"
                                    >
                                        Hủy
                                    </Button>
                                </div>
                            )}

                            <div className="pt-2 flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsAiModalOpen(false)}
                                    disabled={isAiJobLoading}
                                >
                                    Đóng
                                </Button>
                                <Button type="submit" isLoading={isAiJobLoading} className="gap-2">
                                    <Sparkles className="w-4 h-4 text-emerald-300" />
                                    <span>Sinh bộ thẻ ngay</span>
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: View & Add Cards in Deck */}
            {selectedDeckDetail && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-2xl bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden flex flex-col max-h-[85vh]">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <div>
                                <h2 className="text-base font-bold text-slate-900">
                                    {selectedDeckDetail.title}
                                </h2>
                                <p className="text-xs text-slate-500">
                                    Tổng số {selectedDeckDetail.cards.length} thẻ ghi nhớ
                                </p>
                            </div>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setSelectedDeckDetail(null)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>

                        <div className="flex-1 overflow-y-auto p-5 space-y-6">
                            {/* Add Card Form */}
                            <form onSubmit={handleAddCard} className="p-4 rounded-xl bg-slate-50 border border-slate-200 space-y-3">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                                    Thêm thẻ mới vào bộ
                                </h4>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                    <div>
                                        <label className="block text-[11px] font-semibold text-slate-600 mb-1">
                                            Mặt trước (Câu hỏi / Khái niệm)
                                        </label>
                                        <textarea
                                            className="w-full rounded-md border border-slate-200 bg-white p-2 text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary min-h-[60px]"
                                            placeholder="Ví dụ: TCP viết tắt của từ gì?"
                                            value={newCardFront}
                                            onChange={(e) => setNewCardFront(e.target.value)}
                                            required
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-[11px] font-semibold text-slate-600 mb-1">
                                            Mặt sau (Câu trả lời / Giải thích)
                                        </label>
                                        <textarea
                                            className="w-full rounded-md border border-slate-200 bg-white p-2 text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-primary min-h-[60px]"
                                            placeholder="Ví dụ: Transmission Control Protocol..."
                                            value={newCardBack}
                                            onChange={(e) => setNewCardBack(e.target.value)}
                                            required
                                        />
                                    </div>
                                </div>
                                <div className="flex justify-end">
                                    <Button type="submit" size="sm" isLoading={isAddingCard} className="gap-1.5 text-xs">
                                        <Plus className="w-3.5 h-3.5" />
                                        <span>Thêm thẻ</span>
                                    </Button>
                                </div>
                            </form>

                            {/* Cards List */}
                            <div className="space-y-3">
                                {selectedDeckDetail.cards.length === 0 ? (
                                    <p className="text-xs text-slate-400 py-6 text-center">
                                        Bộ thẻ chưa có thẻ nào. Hãy thêm thẻ đầu tiên ở trên.
                                    </p>
                                ) : (
                                    selectedDeckDetail.cards.map((card, i) => (
                                        <div
                                            key={card.id}
                                            className="p-3.5 rounded-xl border border-slate-200 bg-white hover:border-slate-300 transition space-y-1.5 text-xs"
                                        >
                                            <div className="flex items-center justify-between text-[11px] text-slate-400">
                                                <span className="font-bold text-slate-500">Thẻ #{i + 1}</span>
                                                {card.nextReviewAt && (
                                                    <span>
                                                        Ôn tiếp: {new Date(card.nextReviewAt).toLocaleDateString("vi-VN")}
                                                    </span>
                                                )}
                                            </div>
                                            <div className="grid grid-cols-2 gap-4 pt-1">
                                                <div>
                                                    <span className="text-[10px] font-semibold uppercase text-slate-400">
                                                        Mặt trước:
                                                    </span>
                                                    <p className="text-slate-900 font-medium mt-0.5">{card.front}</p>
                                                </div>
                                                <div>
                                                    <span className="text-[10px] font-semibold uppercase text-slate-400">
                                                        Mặt sau:
                                                    </span>
                                                    <p className="text-slate-700 mt-0.5">{card.back}</p>
                                                </div>
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default FlashcardsPage;
