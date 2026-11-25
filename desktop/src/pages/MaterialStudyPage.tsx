import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    BookOpen,
    Bot,
    Check,
    CheckCircle2,
    Copy,
    Download,
    FileText,
    Flag,
    HelpCircle,
    Layers,
    ListFilter,
    MessageSquare,
    Plus,
    RefreshCw,
    Send,
    Sparkles,
    Trash2,
    User as UserIcon,
    X
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import learningService from "../services/learningService";
import aiService from "../services/aiService";
import {
    AiChatMessage,
    AiChatSession,
    AiSourceCitation,
    AiSummary,
    MaterialChunk,
    MaterialDetail,
    SummaryLength
} from "../types";
import { useAiJob } from "../hooks/useAiJob";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Badge } from "../components/ui/badge";
import { Card, CardContent } from "../components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Pagination } from "../components/ui/pagination";
import { EmptyState } from "../components/common/EmptyState";
import { LoadingState } from "../components/common/LoadingState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { toast } from "sonner";

export const MaterialStudyPage: React.FC = () => {
    const { materialId } = useParams<{ materialId: string }>();
    const navigate = useNavigate();
    const id = Number(materialId);

    const [material, setMaterial] = useState<MaterialDetail | null>(null);
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);

    // Active Tab
    const [activeTab, setActiveTab] = useState<"content" | "summary" | "chat" | "explain">("content");

    // -------------------------------------------------------------
    // Tab 1: Extracted Content Chunks
    // -------------------------------------------------------------
    const [chunks, setChunks] = useState<MaterialChunk[]>([]);
    const [chunkPage, setChunkPage] = useState<number>(0);
    const [chunkTotalPages, setChunkTotalPages] = useState<number>(1);
    const [isChunksLoading, setIsChunksLoading] = useState<boolean>(false);
    const [copiedChunkId, setCopiedChunkId] = useState<number | null>(null);

    // -------------------------------------------------------------
    // Tab 2: Summaries
    // -------------------------------------------------------------
    const [summaries, setSummaries] = useState<AiSummary[]>([]);
    const [activeSummary, setActiveSummary] = useState<AiSummary | null>(null);
    const [summaryLength, setSummaryLength] = useState<SummaryLength>("MEDIUM");
    const [copiedSummary, setCopiedSummary] = useState<boolean>(false);

    // Hook useAiJob for Summaries
    const {
        jobId: summaryJobId,
        isLoading: isSummaryJobLoading,
        progressText: summaryProgressText,
        startJob: startSummaryJob,
        cancel: cancelSummaryJob
    } = useAiJob(null, {
        onCompleted: async () => {
            toast.success("Bản tóm tắt đã hoàn thành!");
            await fetchSummaries();
        },
        onError: (err) => toast.error(err)
    });

    // -------------------------------------------------------------
    // Tab 3: Grounded Chat
    // -------------------------------------------------------------
    const [chatSessions, setChatSessions] = useState<AiChatSession[]>([]);
    const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
    const [chatMessages, setChatMessages] = useState<AiChatMessage[]>([]);
    const [chatInput, setChatInput] = useState<string>("");
    const [allowExternalKnowledge, setAllowExternalKnowledge] = useState<boolean>(false);
    const [isSendingMessage, setIsSendingMessage] = useState<boolean>(false);
    const [isNewChatModalOpen, setIsNewChatModalOpen] = useState<boolean>(false);
    const [newChatTitle, setNewChatTitle] = useState<string>("");

    // Feedback modal
    const [feedbackMessageId, setFeedbackMessageId] = useState<number | null>(null);
    const [feedbackReason, setFeedbackReason] = useState<string>("");
    const [isSubmittingFeedback, setIsSubmittingFeedback] = useState<boolean>(false);

    const messagesEndRef = useRef<HTMLDivElement | null>(null);

    // -------------------------------------------------------------
    // Tab 4: Concept Explanation
    // -------------------------------------------------------------
    const [explainConcept, setExplainConcept] = useState<string>("");
    const [explainContextSnippet, setExplainContextSnippet] = useState<string>("");
    const [explainAllowExternal, setExplainAllowExternal] = useState<boolean>(false);
    const [explanationResult, setExplanationResult] = useState<{
        concept: string;
        explanation: string;
        grounded: boolean;
        sources: AiSourceCitation[];
        aiAddedNote: string | null;
        model?: string;
    } | null>(null);

    // Hook useAiJob for Explanation
    const {
        jobId: explainJobId,
        isLoading: isExplainJobLoading,
        progressText: explainProgressText,
        startJob: startExplainJob,
        cancel: cancelExplainJob
    } = useAiJob(null, {
        onCompleted: (completedJob) => {
            toast.success("Giải thích khái niệm thành công!");
            if (completedJob.resultPayload) {
                const payload = completedJob.resultPayload as unknown as {
                    concept: string;
                    explanation: string;
                    grounded: boolean;
                    sources: AiSourceCitation[];
                    aiAddedNote: string | null;
                    model?: string;
                };
                setExplanationResult(payload);
            }
        },
        onError: (err) => toast.error(err)
    });

    // -------------------------------------------------------------
    // Initial Loaders
    // -------------------------------------------------------------
    const fetchMaterial = async () => {
        try {
            setIsLoading(true);
            const data = await learningService.getMaterialDetail(id);
            setMaterial(data);
            setError(null);
            void fetchChunks(0);
            void fetchSummaries();
            void fetchChatSessions(data.courseId);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải thông tin tài liệu.");
        } finally {
            setIsLoading(false);
        }
    };

    const fetchChunks = async (page = 0) => {
        try {
            setIsChunksLoading(true);
            const res = await learningService.getMaterialContent(id, undefined, undefined, page, 5);
            setChunks(res.items);
            if (res.meta?.totalPages) {
                setChunkTotalPages(res.meta.totalPages);
            }
            setChunkPage(page);
        } catch {
            setChunks([]);
        } finally {
            setIsChunksLoading(false);
        }
    };

    const fetchSummaries = async () => {
        try {
            const list = await aiService.listMaterialSummaries(id);
            setSummaries(list);
            if (list.length > 0 && !activeSummary) {
                setActiveSummary(list[0]);
            }
        } catch {
            setSummaries([]);
        }
    };

    const fetchChatSessions = async (courseId: number) => {
        try {
            const res = await aiService.listChats(courseId);
            setChatSessions(res.items);
            if (res.items.length > 0) {
                setActiveSessionId(res.items[0].id);
                void loadChatMessages(res.items[0].id);
            }
        } catch {
            setChatSessions([]);
        }
    };

    const loadChatMessages = async (chatId: number) => {
        try {
            const res = await aiService.getChatDetail(chatId);
            setChatMessages(res.detail.messages);
        } catch {
            setChatMessages([]);
        }
    };

    useEffect(() => {
        if (!isNaN(id)) {
            void fetchMaterial();
        }
    }, [id]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [chatMessages, isSendingMessage]);

    // -------------------------------------------------------------
    // Summary Handlers
    // -------------------------------------------------------------
    const handleGenerateSummary = async () => {
        try {
            const accepted = await aiService.requestSummary({
                materialId: id,
                length: summaryLength
            });
            startSummaryJob(accepted.jobId);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Yêu cầu tóm tắt thất bại.");
        }
    };

    const handleDeleteSummary = async (sumId: number) => {
        if (!window.confirm("Bạn có chắc muốn xóa bản tóm tắt này không?")) return;
        try {
            await aiService.deleteSummary(sumId);
            setSummaries((prev) => prev.filter((s) => s.id !== sumId));
            if (activeSummary?.id === sumId) {
                const remaining = summaries.filter((s) => s.id !== sumId);
                setActiveSummary(remaining.length > 0 ? remaining[0] : null);
            }
            toast.success("Đã xóa bản tóm tắt.");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Xóa thất bại.");
        }
    };

    // -------------------------------------------------------------
    // Chat Handlers
    // -------------------------------------------------------------
    const handleCreateChatSession = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newChatTitle.trim() || !material) return;

        try {
            const newSession = await aiService.createChat({
                title: newChatTitle.trim(),
                courseId: material.courseId
            });
            setChatSessions((prev) => [newSession, ...prev]);
            setActiveSessionId(newSession.id);
            setChatMessages([]);
            setIsNewChatModalOpen(false);
            setNewChatTitle("");
            toast.success("Tạo phiên hỏi đáp thành công!");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Tạo phiên chat thất bại.");
        }
    };

    const handleSendMessage = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!chatInput.trim() || !activeSessionId || isSendingMessage) return;

        const content = chatInput.trim();
        setChatInput("");

        const optimisticUserMsg: AiChatMessage = {
            id: Date.now(),
            role: "USER",
            content,
            grounded: true,
            sources: [],
            aiAddedNote: null,
            createdAt: new Date().toISOString()
        };

        setChatMessages((prev) => [...prev, optimisticUserMsg]);
        setIsSendingMessage(true);

        try {
            const answer = await aiService.sendMessage(activeSessionId, {
                content,
                materialIds: [id],
                allowExternalKnowledge
            });

            const assistantMsg: AiChatMessage = {
                id: answer.messageId,
                role: "ASSISTANT",
                content: answer.answer,
                grounded: answer.grounded,
                sources: answer.sources,
                aiAddedNote: answer.aiAddedNote,
                model: answer.model,
                promptVersion: answer.promptVersion,
                createdAt: answer.createdAt
            };

            setChatMessages((prev) => [...prev, assistantMsg]);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Không nhận được phản hồi từ AI.");
        } finally {
            setIsSendingMessage(false);
        }
    };

    const handleSubmitFeedback = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!feedbackMessageId || !feedbackReason.trim()) return;

        try {
            setIsSubmittingFeedback(true);
            await aiService.submitFeedback({
                targetType: "AI_CHAT_MESSAGE",
                targetId: feedbackMessageId,
                reason: feedbackReason.trim()
            });
            toast.success("Cảm ơn bạn! Báo cáo câu trả lời sai đã được ghi nhận.");
            setFeedbackMessageId(null);
            setFeedbackReason("");
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Gửi báo cáo thất bại.");
        } finally {
            setIsSubmittingFeedback(false);
        }
    };

    // -------------------------------------------------------------
    // Concept Explanation Handlers
    // -------------------------------------------------------------
    const handleStartExplain = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!explainConcept.trim()) return;

        try {
            setExplanationResult(null);
            const accepted = await aiService.requestExplanation({
                materialId: id,
                concept: explainConcept.trim(),
                contextSnippet: explainContextSnippet.trim() || undefined,
                allowExternalKnowledge: explainAllowExternal
            });
            startExplainJob(accepted.jobId);
        } catch (err: unknown) {
            toast.error(err instanceof Error ? err.message : "Yêu cầu giải thích thất bại.");
        }
    };

    if (isLoading) {
        return <LoadingState text="Đang tải không gian học tập AI..." />;
    }

    if (error || !material) {
        return (
            <ErrorBanner
                title="Không thể mở tài liệu"
                message={error || "Tài liệu không tồn tại."}
                onRetry={fetchMaterial}
            />
        );
    }

    return (
        <div className="space-y-6">
            {/* Top Toolbar */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-3 border-b border-slate-200">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(`/courses/${material.courseId}`)}
                        className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-primary transition"
                    >
                        <ArrowLeft className="w-4 h-4" />
                        <span>Môn học</span>
                    </button>
                    <span className="text-slate-300">/</span>
                    <h1 className="text-lg font-bold text-slate-900 truncate max-w-md">
                        {material.fileName}
                    </h1>
                    <Badge
                        variant={
                            material.status === "EXTRACTED"
                                ? "success"
                                : material.status === "EXTRACTING"
                                ? "warning"
                                : "danger"
                        }
                        className="text-xs"
                    >
                        {material.status === "EXTRACTED"
                            ? "Sẵn sàng"
                            : material.status === "EXTRACTING"
                            ? "Đang trích xuất"
                            : "Lỗi"}
                    </Badge>
                </div>
                <div className="flex items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => learningService.downloadMaterial(material.id, material.fileName)}
                        className="gap-1.5 text-xs"
                    >
                        <Download className="w-3.5 h-3.5" />
                        <span>Tải file gốc</span>
                    </Button>
                </div>
            </div>

            {/* Main Tabs */}
            <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as unknown as typeof activeTab)}>
                <TabsList className="grid grid-cols-4 max-w-2xl bg-slate-100 p-1 rounded-xl">
                    <TabsTrigger value="content" className="gap-2 text-xs font-medium">
                        <FileText className="w-4 h-4" />
                        <span>Nội dung tài liệu</span>
                    </TabsTrigger>
                    <TabsTrigger value="summary" className="gap-2 text-xs font-medium">
                        <Sparkles className="w-4 h-4 text-amber-500" />
                        <span>AI Tóm tắt</span>
                    </TabsTrigger>
                    <TabsTrigger value="chat" className="gap-2 text-xs font-medium">
                        <MessageSquare className="w-4 h-4 text-primary" />
                        <span>Hỏi đáp Grounded</span>
                    </TabsTrigger>
                    <TabsTrigger value="explain" className="gap-2 text-xs font-medium">
                        <HelpCircle className="w-4 h-4 text-emerald-600" />
                        <span>Giải thích nhanh</span>
                    </TabsTrigger>
                </TabsList>

                {/* ========================================================= */}
                {/* TAB 1: EXTRACTED CONTENT CHUNKS                           */}
                {/* ========================================================= */}
                <TabsContent value="content" className="pt-4 space-y-4">
                    {isChunksLoading ? (
                        <LoadingState text="Đang tải các đoạn văn bản trích xuất..." />
                    ) : chunks.length === 0 ? (
                        <EmptyState
                            icon={<FileText className="w-8 h-8 text-slate-400" />}
                            title="Tài liệu chưa có nội dung trích xuất"
                            description="Tài liệu có thể đang được hệ thống phân tách văn bản bất đồng bộ hoặc gặp sự cố định dạng."
                            action={
                                <Button
                                    size="sm"
                                    onClick={() => learningService.reextractMaterial(id)}
                                    className="gap-1.5"
                                >
                                    <RefreshCw className="w-3.5 h-3.5" />
                                    <span>Trích xuất lại</span>
                                </Button>
                            }
                        />
                    ) : (
                        <div className="space-y-4">
                            {chunks.map((chunk) => (
                                <Card key={chunk.chunkId} className="border-slate-200 shadow-sm">
                                    <CardContent className="p-5 space-y-3">
                                        <div className="flex items-center justify-between text-xs text-slate-500 pb-2 border-b border-slate-100">
                                            <div className="flex items-center gap-2">
                                                <span className="font-semibold px-2 py-0.5 rounded bg-blue-50 text-primary">
                                                    Đoạn #{chunk.chunkIndex + 1}
                                                </span>
                                                <span>
                                                    Trang {chunk.pageFrom} - {chunk.pageTo}
                                                </span>
                                                <span className="text-slate-300">•</span>
                                                <span>{chunk.tokenCount} tokens</span>
                                            </div>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => {
                                                    navigator.clipboard.writeText(chunk.content);
                                                    setCopiedChunkId(chunk.chunkId);
                                                    setTimeout(() => setCopiedChunkId(null), 2000);
                                                }}
                                                className="h-7 px-2 text-xs text-slate-500 gap-1"
                                            >
                                                {copiedChunkId === chunk.chunkId ? (
                                                    <>
                                                        <Check className="w-3 h-3 text-emerald-600" />
                                                        <span className="text-emerald-600">Đã chép</span>
                                                    </>
                                                ) : (
                                                    <>
                                                        <Copy className="w-3 h-3" />
                                                        <span>Sao chép</span>
                                                    </>
                                                )}
                                            </Button>
                                        </div>
                                        <p className="text-sm text-slate-800 leading-relaxed whitespace-pre-wrap font-sans">
                                            {chunk.content}
                                        </p>
                                    </CardContent>
                                </Card>
                            ))}

                            {chunkTotalPages > 1 && (
                                <div className="pt-2 flex justify-center">
                                    <Pagination
                                        currentPage={chunkPage}
                                        totalPages={chunkTotalPages}
                                        onPageChange={(p) => fetchChunks(p)}
                                    />
                                </div>
                            )}
                        </div>
                    )}
                </TabsContent>

                {/* ========================================================= */}
                {/* TAB 2: AI SUMMARY                                         */}
                {/* ========================================================= */}
                <TabsContent value="summary" className="pt-4 space-y-6">
                    {/* Action Bar */}
                    <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="flex items-center gap-3">
                            <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">
                                Mức độ tóm tắt:
                            </span>
                            <div className="inline-flex rounded-lg border border-slate-200 p-0.5 bg-slate-50">
                                {(["SHORT", "MEDIUM", "DETAILED"] as SummaryLength[]).map((len) => (
                                    <button
                                        key={len}
                                        onClick={() => setSummaryLength(len)}
                                        className={`px-3 py-1.5 rounded-md text-xs font-semibold transition ${
                                            summaryLength === len
                                                ? "bg-white text-primary shadow-sm"
                                                : "text-slate-600 hover:text-slate-900"
                                        }`}
                                    >
                                        {len === "SHORT" ? "Ngắn gọn" : len === "MEDIUM" ? "Vừa phải" : "Chi tiết"}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <Button
                            onClick={handleGenerateSummary}
                            isLoading={isSummaryJobLoading}
                            className="gap-2"
                        >
                            <Sparkles className="w-4 h-4 text-amber-300" />
                            <span>Tạo tóm tắt bằng AI</span>
                        </Button>
                    </div>

                    {/* In-Progress Alert */}
                    {isSummaryJobLoading && (
                        <div className="p-4 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-between gap-3">
                            <div className="flex items-center gap-2.5">
                                <Sparkles className="w-4 h-4 text-primary animate-spin" />
                                <span className="text-xs font-medium text-slate-800">
                                    {summaryProgressText}
                                </span>
                            </div>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={cancelSummaryJob}
                                className="h-7 text-xs text-rose-600 border-rose-200 hover:bg-rose-50"
                            >
                                Hủy
                            </Button>
                        </div>
                    )}

                    {/* Summary Content & History Layout */}
                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                        {/* Summary Markdown Viewer */}
                        <div className="lg:col-span-8 space-y-4">
                            {activeSummary ? (
                                <Card className="border-slate-200 shadow-sm">
                                    <CardContent className="p-6 md:p-8 space-y-4">
                                        <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                                            <div className="flex items-center gap-2">
                                                <Badge variant="default" className="text-xs">
                                                    {activeSummary.length === "SHORT"
                                                        ? "Tóm tắt ngắn"
                                                        : activeSummary.length === "MEDIUM"
                                                        ? "Tóm tắt vừa"
                                                        : "Tóm tắt chi tiết"}
                                                </Badge>
                                                <Badge variant="outline" className="text-[11px] text-slate-500">
                                                    Model: {activeSummary.model}
                                                </Badge>
                                                <span className="text-xs text-slate-400">
                                                    {new Date(activeSummary.createdAt).toLocaleString("vi-VN")}
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => {
                                                        navigator.clipboard.writeText(activeSummary.content);
                                                        setCopiedSummary(true);
                                                        setTimeout(() => setCopiedSummary(false), 2000);
                                                    }}
                                                    className="h-8 gap-1 text-xs text-slate-600"
                                                >
                                                    {copiedSummary ? (
                                                        <>
                                                            <Check className="w-3.5 h-3.5 text-emerald-600" />
                                                            <span className="text-emerald-600">Đã chép</span>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <Copy className="w-3.5 h-3.5" />
                                                            <span>Sao chép</span>
                                                        </>
                                                    )}
                                                </Button>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => handleDeleteSummary(activeSummary.id)}
                                                    className="h-8 w-8 p-0 text-rose-500 hover:text-rose-700"
                                                    title="Xóa bản tóm tắt"
                                                >
                                                    <Trash2 className="w-3.5 h-3.5" />
                                                </Button>
                                            </div>
                                        </div>

                                        <div className="prose prose-slate max-w-none text-sm leading-relaxed">
                                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                                {activeSummary.content}
                                            </ReactMarkdown>
                                        </div>
                                    </CardContent>
                                </Card>
                            ) : (
                                <EmptyState
                                    icon={<Sparkles className="w-8 h-8 text-amber-500" />}
                                    title="Chưa có bản tóm tắt nào"
                                    description="Chọn độ dài mong muốn và bấm 'Tạo tóm tắt bằng AI' để mô hình phân tích và tổng hợp ý chính của tài liệu."
                                    action={
                                        <Button onClick={handleGenerateSummary} className="gap-2">
                                            <Sparkles className="w-4 h-4 text-amber-300" />
                                            <span>Tạo tóm tắt ngay</span>
                                        </Button>
                                    }
                                />
                            )}
                        </div>

                        {/* History Side List */}
                        <div className="lg:col-span-4 space-y-3">
                            <Card>
                                <CardContent className="p-4 space-y-3">
                                    <h3 className="font-semibold text-slate-900 text-xs uppercase tracking-wider">
                                        Lịch sử các bản tóm tắt ({summaries.length})
                                    </h3>
                                    {summaries.length === 0 ? (
                                        <p className="text-xs text-slate-400 py-2">
                                            Chưa có phiên tóm tắt nào được lưu.
                                        </p>
                                    ) : (
                                        <div className="space-y-2">
                                            {summaries.map((sum) => (
                                                <div
                                                    key={sum.id}
                                                    onClick={() => setActiveSummary(sum)}
                                                    className={`p-3 rounded-lg border text-xs cursor-pointer transition flex items-center justify-between gap-2 ${
                                                        activeSummary?.id === sum.id
                                                            ? "bg-blue-50 border-primary/40 text-primary font-medium"
                                                            : "bg-white border-slate-200 text-slate-700 hover:bg-slate-50"
                                                    }`}
                                                >
                                                    <div>
                                                        <p className="font-semibold">
                                                            {sum.length === "SHORT"
                                                                ? "Bản tóm tắt ngắn"
                                                                : sum.length === "MEDIUM"
                                                                ? "Bản tóm tắt vừa"
                                                                : "Bản tóm tắt chi tiết"}
                                                        </p>
                                                        <p className="text-[10px] text-slate-400 mt-0.5">
                                                            {new Date(sum.createdAt).toLocaleDateString("vi-VN")}
                                                        </p>
                                                    </div>
                                                    <span className="text-[10px] font-mono text-slate-400">
                                                        #{sum.id}
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    )}
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                </TabsContent>

                {/* ========================================================= */}
                {/* TAB 3: GROUNDED CHAT                                      */}
                {/* ========================================================= */}
                <TabsContent value="chat" className="pt-4 space-y-4">
                    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                        {/* Left: Chat Sessions List */}
                        <div className="lg:col-span-3 space-y-3">
                            <Card>
                                <CardContent className="p-3 space-y-2">
                                    <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                                        <span className="text-xs font-bold uppercase tracking-wider text-slate-700">
                                            Phiên hỏi đáp
                                        </span>
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            onClick={() => setIsNewChatModalOpen(true)}
                                            className="h-7 w-7 p-0 text-primary"
                                            title="Tạo phiên mới"
                                        >
                                            <Plus className="w-4 h-4" />
                                        </Button>
                                    </div>

                                    {chatSessions.length === 0 ? (
                                        <p className="text-xs text-slate-400 py-3 text-center">
                                            Chưa có phiên chat nào. Bấm "+" để bắt đầu.
                                        </p>
                                    ) : (
                                        <div className="space-y-1.5 max-h-[420px] overflow-y-auto">
                                            {chatSessions.map((session) => (
                                                <button
                                                    key={session.id}
                                                    onClick={() => {
                                                        setActiveSessionId(session.id);
                                                        void loadChatMessages(session.id);
                                                    }}
                                                    className={`w-full text-left p-2.5 rounded-lg text-xs truncate transition block ${
                                                        activeSessionId === session.id
                                                            ? "bg-primary text-white font-medium"
                                                            : "text-slate-700 hover:bg-slate-100"
                                                    }`}
                                                >
                                                    {session.title}
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </CardContent>
                            </Card>

                            {/* Knowledge Grounding Toggle */}
                            <Card>
                                <CardContent className="p-3.5 space-y-2 text-xs">
                                    <div className="flex items-center justify-between">
                                        <span className="font-semibold text-slate-800">
                                            Mở rộng kiến thức ngoài
                                        </span>
                                        <input
                                            type="checkbox"
                                            checked={allowExternalKnowledge}
                                            onChange={(e) => setAllowExternalKnowledge(e.target.checked)}
                                            className="rounded border-slate-300 text-primary focus:ring-primary h-4 w-4"
                                        />
                                    </div>
                                    <p className="text-[11px] text-slate-500 leading-relaxed">
                                        Khi tắt, AI chỉ trả lời dựa trên tài liệu. Khi bật, phần kiến thức bổ sung sẽ được tách riêng rõ ràng.
                                    </p>
                                </CardContent>
                            </Card>
                        </div>

                        {/* Right: Messages Thread */}
                        <div className="lg:col-span-9">
                            <Card className="flex flex-col h-[600px] border-slate-200 shadow-sm overflow-hidden">
                                {/* Chat Header */}
                                <div className="px-5 py-3 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between text-xs text-slate-600">
                                    <div className="flex items-center gap-2">
                                        <Bot className="w-4 h-4 text-primary" />
                                        <span className="font-semibold text-slate-800">
                                            {chatSessions.find((s) => s.id === activeSessionId)?.title || "Phiên chat AI"}
                                        </span>
                                    </div>
                                    <span className="text-[11px] text-slate-400">
                                        Bám sát tài liệu: {material.fileName}
                                    </span>
                                </div>

                                {/* Messages Scroll Area */}
                                <div className="flex-1 overflow-y-auto p-5 space-y-4">
                                    {chatMessages.length === 0 ? (
                                        <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
                                            <Bot className="w-12 h-12 text-slate-300 mb-2" />
                                            <p className="font-semibold text-slate-700 text-sm">
                                                Đặt câu hỏi về tài liệu này
                                            </p>
                                            <p className="text-xs text-slate-500 max-w-sm mt-1">
                                                Hỏi định nghĩa, so sánh hoặc tóm tắt một đoạn cụ thể. AI sẽ trả lời kèm trích dẫn số trang chính xác.
                                            </p>
                                        </div>
                                    ) : (
                                        chatMessages.map((msg) => (
                                            <div
                                                key={msg.id}
                                                className={`flex gap-3 ${
                                                    msg.role === "USER" ? "justify-end" : "justify-start"
                                                }`}
                                            >
                                                {msg.role === "ASSISTANT" && (
                                                    <div className="w-8 h-8 rounded-full bg-blue-100 text-primary flex items-center justify-center shrink-0 text-xs font-bold">
                                                        <Bot className="w-4 h-4" />
                                                    </div>
                                                )}

                                                <div
                                                    className={`max-w-2xl rounded-2xl p-4 text-sm ${
                                                        msg.role === "USER"
                                                            ? "bg-primary text-white"
                                                            : "bg-slate-100 text-slate-900 border border-slate-200/80"
                                                    }`}
                                                >
                                                    {/* Grounded Warning */}
                                                    {msg.role === "ASSISTANT" && !msg.grounded && (
                                                        <div className="mb-2.5 p-2 rounded-lg bg-amber-50 border border-amber-200 text-xs text-amber-800 flex items-start gap-1.5">
                                                            <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                                                            <span>
                                                                Tài liệu hiện tại chưa có thông tin rõ ràng về câu hỏi này.
                                                            </span>
                                                        </div>
                                                    )}

                                                    {/* Message Content */}
                                                    <div className="prose prose-sm max-w-none">
                                                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                                            {msg.content}
                                                        </ReactMarkdown>
                                                    </div>

                                                    {/* AI Added Note */}
                                                    {msg.aiAddedNote && (
                                                        <div className="mt-3 p-2.5 rounded-lg bg-blue-50/80 border border-blue-200/60 text-xs text-blue-900">
                                                            <div className="font-semibold flex items-center gap-1 mb-1">
                                                                <Sparkles className="w-3 h-3 text-primary" />
                                                                <span>Phần AI giải thích mở rộng:</span>
                                                            </div>
                                                            <p className="leading-relaxed">{msg.aiAddedNote}</p>
                                                        </div>
                                                    )}

                                                    {/* Sources Citations */}
                                                    {msg.sources && msg.sources.length > 0 && (
                                                        <div className="mt-3 pt-2 border-t border-slate-200/60 flex flex-wrap items-center gap-1.5">
                                                            <span className="text-[11px] font-semibold text-slate-500">
                                                                Nguồn trích dẫn:
                                                            </span>
                                                            {msg.sources.map((src, idx) => (
                                                                <Badge
                                                                    key={idx}
                                                                    variant="outline"
                                                                    className="text-[10px] bg-white py-0.5 px-2"
                                                                >
                                                                    {src.fileName} (Trang {src.page})
                                                                </Badge>
                                                            ))}
                                                        </div>
                                                    )}

                                                    {/* Report inaccurate answer button */}
                                                    {msg.role === "ASSISTANT" && (
                                                        <div className="mt-2.5 pt-2 border-t border-slate-200/40 flex justify-end">
                                                            <button
                                                                onClick={() => setFeedbackMessageId(msg.id)}
                                                                className="inline-flex items-center gap-1 text-[11px] text-slate-400 hover:text-rose-600 transition"
                                                            >
                                                                <Flag className="w-3 h-3" />
                                                                <span>Báo câu trả lời sai</span>
                                                            </button>
                                                        </div>
                                                    )}
                                                </div>

                                                {msg.role === "USER" && (
                                                    <div className="w-8 h-8 rounded-full bg-slate-200 text-slate-700 flex items-center justify-center shrink-0 text-xs font-bold">
                                                        <UserIcon className="w-4 h-4" />
                                                    </div>
                                                )}
                                            </div>
                                        ))
                                    )}

                                    {isSendingMessage && (
                                        <div className="flex items-center gap-2 text-xs text-slate-400 pl-11">
                                            <Sparkles className="w-3.5 h-3.5 text-primary animate-spin" />
                                            <span>AI đang tra cứu tài liệu và trả lời...</span>
                                        </div>
                                    )}
                                    <div ref={messagesEndRef} />
                                </div>

                                {/* Chat Input Bar */}
                                <form onSubmit={handleSendMessage} className="p-3 border-t border-slate-200 bg-white flex items-center gap-2">
                                    <Input
                                        type="text"
                                        placeholder="Nhập câu hỏi của bạn về tài liệu..."
                                        value={chatInput}
                                        onChange={(e) => setChatInput(e.target.value)}
                                        disabled={isSendingMessage || !activeSessionId}
                                        className="flex-1"
                                    />
                                    <Button
                                        type="submit"
                                        disabled={!chatInput.trim() || isSendingMessage || !activeSessionId}
                                        className="gap-1.5 shrink-0"
                                    >
                                        <Send className="w-4 h-4" />
                                        <span>Gửi</span>
                                    </Button>
                                </form>
                            </Card>
                        </div>
                    </div>
                </TabsContent>

                {/* ========================================================= */}
                {/* TAB 4: CONCEPT EXPLANATION                                */}
                {/* ========================================================= */}
                <TabsContent value="explain" className="pt-4 space-y-6">
                    <Card className="border-slate-200 shadow-sm max-w-2xl mx-auto">
                        <CardContent className="p-6 space-y-4">
                            <div>
                                <h3 className="font-bold text-slate-900 text-base flex items-center gap-2">
                                    <HelpCircle className="w-5 h-5 text-emerald-600" />
                                    <span>Giải thích nhanh khái niệm theo tài liệu</span>
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    Nhập thuật ngữ hoặc đoạn văn khó hiểu để AI giải thích ngắn gọn, dễ hiểu kèm trích dẫn
                                </p>
                            </div>

                            <form onSubmit={handleStartExplain} className="space-y-4 pt-2">
                                <div>
                                    <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                        Thuật ngữ / Khái niệm cần giải thích <span className="text-rose-500">*</span>
                                    </label>
                                    <Input
                                        type="text"
                                        placeholder="Ví dụ: Three-way Handshake, Sliding Window, Congestion Window..."
                                        value={explainConcept}
                                        onChange={(e) => setExplainConcept(e.target.value)}
                                        required
                                        disabled={isExplainJobLoading}
                                    />
                                </div>

                                <div>
                                    <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                        Đoạn ngữ cảnh trong tài liệu (không bắt buộc)
                                    </label>
                                    <textarea
                                        className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary min-h-[70px]"
                                        placeholder="Dán câu hoặc đoạn liên quan nếu có để AI bám sát..."
                                        value={explainContextSnippet}
                                        onChange={(e) => setExplainContextSnippet(e.target.value)}
                                        disabled={isExplainJobLoading}
                                    />
                                </div>

                                <div className="flex items-center gap-2 text-xs">
                                    <input
                                        type="checkbox"
                                        id="explainAllowExternal"
                                        checked={explainAllowExternal}
                                        onChange={(e) => setExplainAllowExternal(e.target.checked)}
                                        disabled={isExplainJobLoading}
                                        className="rounded border-slate-300 text-primary focus:ring-primary"
                                    />
                                    <label htmlFor="explainAllowExternal" className="text-slate-700 cursor-pointer">
                                        Cho phép AI bổ sung kiến thức mở rộng ngoài tài liệu nếu tài liệu chưa đầy đủ
                                    </label>
                                </div>

                                {isExplainJobLoading && (
                                    <div className="p-3.5 rounded-xl bg-blue-50 border border-blue-100 flex items-center justify-between text-xs text-slate-800">
                                        <div className="flex items-center gap-2">
                                            <Sparkles className="w-4 h-4 text-primary animate-spin" />
                                            <span>{explainProgressText}</span>
                                        </div>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            onClick={cancelExplainJob}
                                            className="h-7 text-xs text-rose-600 border-rose-200"
                                        >
                                            Hủy
                                        </Button>
                                    </div>
                                )}

                                <Button
                                    type="submit"
                                    isLoading={isExplainJobLoading}
                                    className="w-full gap-2"
                                >
                                    <Sparkles className="w-4 h-4 text-emerald-300" />
                                    <span>Giải thích khái niệm</span>
                                </Button>
                            </form>
                        </CardContent>
                    </Card>

                    {/* Explanation Result Card */}
                    {explanationResult && (
                        <Card className="border-slate-200 shadow-sm max-w-2xl mx-auto animate-in fade-in">
                            <CardContent className="p-6 md:p-8 space-y-4">
                                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                                    <div className="flex items-center gap-2">
                                        <h4 className="font-bold text-slate-900 text-base">
                                            {explanationResult.concept}
                                        </h4>
                                        <Badge
                                            variant={explanationResult.grounded ? "success" : "warning"}
                                            className="text-xs"
                                        >
                                            {explanationResult.grounded ? "Bám sát tài liệu" : "Kiến thức mở rộng"}
                                        </Badge>
                                    </div>
                                    {explanationResult.model && (
                                        <span className="text-[11px] text-slate-400">
                                            {explanationResult.model}
                                        </span>
                                    )}
                                </div>

                                <div className="prose prose-sm max-w-none text-slate-800 leading-relaxed">
                                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                        {explanationResult.explanation}
                                    </ReactMarkdown>
                                </div>

                                {explanationResult.aiAddedNote && (
                                    <div className="p-3 rounded-lg bg-blue-50 border border-blue-200 text-xs text-blue-900">
                                        <span className="font-semibold block mb-0.5">
                                            Giải thích mở rộng thêm:
                                        </span>
                                        <p>{explanationResult.aiAddedNote}</p>
                                    </div>
                                )}

                                {explanationResult.sources && explanationResult.sources.length > 0 && (
                                    <div className="pt-2 border-t border-slate-100 flex flex-wrap items-center gap-1.5 text-xs">
                                        <span className="font-semibold text-slate-500">Trích từ:</span>
                                        {explanationResult.sources.map((src, i) => (
                                            <Badge key={i} variant="outline" className="text-[10px]">
                                                {src.fileName} (Trang {src.page})
                                            </Badge>
                                        ))}
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    )}
                </TabsContent>
            </Tabs>

            {/* Modal: New Chat Session */}
            {isNewChatModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-sm bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900">Tạo phiên hỏi đáp mới</h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsNewChatModalOpen(false)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleCreateChatSession} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Tiêu đề phiên hỏi đáp <span className="text-rose-500">*</span>
                                </label>
                                <Input
                                    type="text"
                                    placeholder="Ví dụ: Ôn tập chương 3, Hỏi về TCP..."
                                    value={newChatTitle}
                                    onChange={(e) => setNewChatTitle(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div className="flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setIsNewChatModalOpen(false)}
                                >
                                    Hủy
                                </Button>
                                <Button type="submit">Bắt đầu</Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal: Submit Feedback */}
            {feedbackMessageId !== null && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-in fade-in">
                    <div className="w-full max-w-md bg-white rounded-2xl shadow-elevated border border-slate-200 overflow-hidden">
                        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
                            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                                <Flag className="w-4 h-4 text-rose-500" />
                                <span>Báo câu trả lời AI sai hoặc không bám tài liệu</span>
                            </h2>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setFeedbackMessageId(null)}
                                className="h-7 w-7 p-0"
                            >
                                <X className="w-4 h-4" />
                            </Button>
                        </div>
                        <form onSubmit={handleSubmitFeedback} className="p-5 space-y-4">
                            <div>
                                <label className="block text-xs font-semibold uppercase text-slate-600 mb-1.5">
                                    Lý do / Điểm chưa chính xác <span className="text-rose-500">*</span>
                                </label>
                                <textarea
                                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary min-h-[90px]"
                                    placeholder="Mô tả cụ thể câu trả lời trích dẫn sai trang hoặc hiểu sai khái niệm..."
                                    value={feedbackReason}
                                    onChange={(e) => setFeedbackReason(e.target.value)}
                                    required
                                    autoFocus
                                />
                            </div>
                            <div className="flex items-center justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setFeedbackMessageId(null)}
                                    disabled={isSubmittingFeedback}
                                >
                                    Hủy
                                </Button>
                                <Button
                                    type="submit"
                                    variant="danger"
                                    isLoading={isSubmittingFeedback}
                                >
                                    Gửi báo cáo
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MaterialStudyPage;
