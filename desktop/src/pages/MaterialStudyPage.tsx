import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    ArrowLeft,
    Bot,
    Check,
    Copy,
    Download,
    FileText,
    ListFilter,
    MessageSquare,
    Send,
    Sparkles,
    User as UserIcon
} from "lucide-react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import aiService from "../services/aiService";
import materialService from "../services/materialService";
import {
    ChatMessage,
    ChatSession,
    LengthType,
    MaterialDetail,
    Summary
} from "../types";
import LoadingSpinner from "../components/common/LoadingSpinner";

export const MaterialStudyPage: React.FC = () => {
    const { materialId } = useParams<{ materialId: string }>();
    const navigate = useNavigate();
    const id = Number(materialId);

    const [material, setMaterial] = useState<MaterialDetail | null>(null);
    const [activeTab, setActiveTab] = useState<"document" | "summary" | "chat">("document");
    const [isLoading, setIsLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [copied, setCopied] = useState<boolean>(false);

    // Summary Tab State
    const [summaries, setSummaries] = useState<Summary[]>([]);
    const [selectedLength, setSelectedLength] = useState<LengthType>("MEDIUM");
    const [isGeneratingSummary, setIsGeneratingSummary] = useState<boolean>(false);
    const [summaryError, setSummaryError] = useState<string | null>(null);

    // Chat Tab State
    const [chatSessions, setChatSessions] = useState<ChatSession[]>([]);
    const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
    const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
    const [chatInput, setChatInput] = useState<string>("");
    const [isSendingMessage, setIsSendingMessage] = useState<boolean>(false);
    const messagesEndRef = useRef<HTMLDivElement | null>(null);

    const fetchInitialData = async () => {
        try {
            setIsLoading(true);
            const [matData, sumData, sessionsData] = await Promise.all([
                materialService.getMaterialDetail(id),
                aiService.getMaterialSummaries(id),
                aiService.getChatSessions()
            ]);

            setMaterial(matData);
            setSummaries(sumData);

            // Filter sessions for this material or course
            const materialSessions = sessionsData.filter((s) => s.materialId === id);
            setChatSessions(materialSessions);

            if (materialSessions.length > 0) {
                setActiveSessionId(materialSessions[0].id);
            }
            setError(null);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : "Không thể tải tài liệu.");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        if (!isNaN(id)) {
            fetchInitialData();
        }
    }, [id]);

    // Load messages when active session changes
    useEffect(() => {
        if (activeSessionId) {
            const loadMessages = async () => {
                try {
                    const messages = await aiService.getChatMessages(activeSessionId);
                    setChatMessages(messages);
                } catch {
                    setChatMessages([]);
                }
            };
            loadMessages();
        } else {
            setChatMessages([]);
        }
    }, [activeSessionId]);

    // Scroll to bottom of chat
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [chatMessages, isSendingMessage]);

    // Copy extracted text
    const handleCopyText = () => {
        if (material?.extractedText) {
            navigator.clipboard.writeText(material.extractedText);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        }
    };

    // Download original file
    const handleDownload = async () => {
        if (material) {
            try {
                await materialService.downloadMaterial(material.id, material.originalName);
            } catch (err: unknown) {
                alert(err instanceof Error ? err.message : "Tải tệp thất bại.");
            }
        }
    };

    // Generate Summary
    const handleGenerateSummary = async () => {
        setSummaryError(null);
        try {
            setIsGeneratingSummary(true);
            const newSummary = await aiService.generateSummary({
                materialId: id,
                lengthType: selectedLength
            });
            setSummaries((prev) => [newSummary, ...prev]);
        } catch (err: unknown) {
            setSummaryError(err instanceof Error ? err.message : "Không thể tạo tóm tắt bằng AI.");
        } finally {
            setIsGeneratingSummary(false);
        }
    };

    // Create Chat Session
    const handleCreateChatSession = async () => {
        if (!material) return;
        try {
            const sessionTitle = `Hỏi đáp: ${material.originalName}`;
            const newSession = await aiService.createChatSession({
                title: sessionTitle,
                courseId: material.courseId,
                materialId: material.id
            });
            setChatSessions((prev) => [newSession, ...prev]);
            setActiveSessionId(newSession.id);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Không thể tạo phiên chat.");
        }
    };

    // Send Message
    const handleSendMessage = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!chatInput.trim() || isSendingMessage) return;

        let currentSessionId = activeSessionId;
        if (!currentSessionId) {
            // Auto create session if none exists
            if (!material) return;
            try {
                const newSession = await aiService.createChatSession({
                    title: `Hỏi đáp: ${material.originalName}`,
                    courseId: material.courseId,
                    materialId: material.id
                });
                setChatSessions([newSession]);
                setActiveSessionId(newSession.id);
                currentSessionId = newSession.id;
            } catch {
                alert("Không thể khởi tạo phiên chat tự động.");
                return;
            }
        }

        const question = chatInput.trim();
        setChatInput("");
        setIsSendingMessage(true);

        try {
            const answerResponse = await aiService.sendMessage(currentSessionId, question);
            setChatMessages((prev) => [
                ...prev,
                answerResponse.userMessage,
                answerResponse.assistantMessage
            ]);
        } catch (err: unknown) {
            alert(err instanceof Error ? err.message : "Gửi tin nhắn thất bại.");
        } finally {
            setIsSendingMessage(false);
        }
    };

    if (isLoading) {
        return <LoadingSpinner size="lg" text="Đang mở không gian học tập AI..." className="py-20" />;
    }

    if (error || !material) {
        return (
            <div className="p-6 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700">
                <p className="font-semibold">Lỗi</p>
                <p className="text-sm mt-1">{error || "Tài liệu không tồn tại."}</p>
                <button onClick={() => navigate(-1)} className="mt-4 text-sm font-semibold underline">
                    Quay lại
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Top Bar Navigation */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <button
                    onClick={() => navigate(`/courses/${material.courseId}`)}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 hover:text-indigo-600 transition"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span>Quay lại trang khóa học</span>
                </button>

                <div className="flex items-center gap-2">
                    <button
                        onClick={handleDownload}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 hover:bg-slate-100 text-slate-700 text-xs font-semibold transition"
                    >
                        <Download className="w-4 h-4" />
                        <span>Tải file gốc</span>
                    </button>
                </div>
            </div>

            {/* Document Header Card */}
            <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-start gap-4">
                    <div className="w-12 h-12 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                        <FileText className="w-6 h-6" />
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-800">
                                {material.fileType.toUpperCase()}
                            </span>
                            <span className="text-xs text-slate-400">
                                {(material.fileSize / 1024).toFixed(1)} KB
                            </span>
                        </div>
                        <h1 className="text-lg md:text-xl font-bold text-slate-800 mt-1">
                            {material.originalName}
                        </h1>
                    </div>
                </div>

                {/* Tabs switcher buttons */}
                <div className="flex p-1 rounded-xl bg-slate-100 border border-slate-200/80 self-start md:self-auto">
                    <button
                        onClick={() => setActiveTab("document")}
                        className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition ${
                            activeTab === "document"
                                ? "bg-white text-indigo-600 shadow-sm"
                                : "text-slate-600 hover:text-slate-900"
                        }`}
                    >
                        <FileText className="w-4 h-4" />
                        <span>Văn bản trích xuất</span>
                    </button>
                    <button
                        onClick={() => setActiveTab("summary")}
                        className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition ${
                            activeTab === "summary"
                                ? "bg-white text-indigo-600 shadow-sm"
                                : "text-slate-600 hover:text-slate-900"
                        }`}
                    >
                        <Sparkles className="w-4 h-4" />
                        <span>Tóm tắt AI</span>
                    </button>
                    <button
                        onClick={() => setActiveTab("chat")}
                        className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition ${
                            activeTab === "chat"
                                ? "bg-white text-indigo-600 shadow-sm"
                                : "text-slate-600 hover:text-slate-900"
                        }`}
                    >
                        <MessageSquare className="w-4 h-4" />
                        <span>Hỏi đáp Gia sư AI</span>
                    </button>
                </div>
            </div>

            {/* Tab 1: Extracted Text Content */}
            {activeTab === "document" && (
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-4">
                    <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                            Toàn văn nội dung tài liệu học tập
                        </span>
                        <button
                            onClick={handleCopyText}
                            className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg text-xs font-medium text-slate-600 hover:bg-slate-100 transition"
                        >
                            {copied ? (
                                <>
                                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                                    <span className="text-emerald-600">Đã sao chép</span>
                                </>
                            ) : (
                                <>
                                    <Copy className="w-3.5 h-3.5" />
                                    <span>Sao chép văn bản</span>
                                </>
                            )}
                        </button>
                    </div>

                    <div className="p-4 bg-slate-50 rounded-xl max-h-[600px] overflow-y-auto font-mono text-xs leading-relaxed text-slate-700 whitespace-pre-wrap select-text">
                        {material.extractedText || "Không có văn bản nào được trích xuất."}
                    </div>
                </div>
            )}

            {/* Tab 2: AI Summaries */}
            {activeTab === "summary" && (
                <div className="space-y-6">
                    {/* Generator Controller Card */}
                    <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm space-y-4">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                            <div>
                                <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
                                    <Sparkles className="w-5 h-5 text-indigo-600" />
                                    <span>Tạo tóm tắt tài liệu bằng Gemini AI</span>
                                </h3>
                                <p className="text-xs text-slate-500 mt-0.5">
                                    Lựa chọn mức độ tóm tắt phù hợp với nhu cầu ôn tập của bạn
                                </p>
                            </div>

                            <button
                                onClick={handleGenerateSummary}
                                disabled={isGeneratingSummary}
                                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white font-semibold text-xs shadow-sm transition disabled:opacity-50"
                            >
                                {isGeneratingSummary ? (
                                    <>
                                        <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                                        <span>AI đang phân tích & tóm tắt...</span>
                                    </>
                                ) : (
                                    <>
                                        <Sparkles className="w-4 h-4" />
                                        <span>Tạo tóm tắt ngay</span>
                                    </>
                                )}
                            </button>
                        </div>

                        {/* Length selection options */}
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-2">
                            {[
                                {
                                    type: "SHORT" as LengthType,
                                    label: "Tóm tắt ngắn (Gạch đầu dòng)",
                                    desc: "3 - 5 ý chính cốt lõi nhất, phù hợp đọc lướt nhanh."
                                },
                                {
                                    type: "MEDIUM" as LengthType,
                                    label: "Tóm tắt tiêu chuẩn (Cấu trúc)",
                                    desc: "Khái niệm, công thức, luận điểm trọng tâm cân đối."
                                },
                                {
                                    type: "DETAILED" as LengthType,
                                    label: "Tóm tắt chuyên sâu (Chi tiết)",
                                    desc: "Phân tích đầy đủ từng khía cạnh, công thức và ứng dụng."
                                }
                            ].map((item) => (
                                <div
                                    key={item.type}
                                    onClick={() => setSelectedLength(item.type)}
                                    className={`p-3.5 rounded-xl border cursor-pointer transition flex flex-col justify-between ${
                                        selectedLength === item.type
                                            ? "border-indigo-600 bg-indigo-50/60 ring-2 ring-indigo-500/20"
                                            : "border-slate-200 hover:border-slate-300 bg-white"
                                    }`}
                                >
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-xs font-bold text-slate-800">{item.label}</span>
                                        <div
                                            className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center ${
                                                selectedLength === item.type
                                                    ? "border-indigo-600 bg-indigo-600 text-white"
                                                    : "border-slate-300"
                                            }`}
                                        >
                                            {selectedLength === item.type && (
                                                <div className="w-1.5 h-1.5 rounded-full bg-white" />
                                            )}
                                        </div>
                                    </div>
                                    <p className="text-[11px] text-slate-500 leading-normal">{item.desc}</p>
                                </div>
                            ))}
                        </div>

                        {summaryError && (
                            <div className="p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-xs">
                                {summaryError}
                            </div>
                        )}
                    </div>

                    {/* Saved Summaries List */}
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <h4 className="text-sm font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                                <ListFilter className="w-4 h-4" />
                                <span>Bản tóm tắt đã lưu ({summaries.length})</span>
                            </h4>
                        </div>

                        {summaries.length > 0 ? (
                            summaries.map((s) => (
                                <div
                                    key={s.id}
                                    className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-3"
                                >
                                    <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase bg-indigo-100 text-indigo-800">
                                            Mức độ: {s.lengthType}
                                        </span>
                                        <span className="text-xs text-slate-400">
                                            {new Date(s.createdAt).toLocaleString("vi-VN")}
                                        </span>
                                    </div>

                                    {/* Render markdown summary content */}
                                    <div className="prose prose-sm prose-indigo max-w-none text-slate-700 leading-relaxed font-sans">
                                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                            {s.content}
                                        </ReactMarkdown>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div className="bg-white rounded-2xl border border-dashed border-slate-200 p-10 text-center text-slate-400">
                                <Sparkles className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                                <p className="text-sm font-medium text-slate-600">Chưa có bản tóm tắt nào</p>
                                <p className="text-xs text-slate-400 mt-0.5">
                                    Bấm &ldquo;Tạo tóm tắt ngay&rdquo; ở trên để AI phân tích tài liệu của bạn.
                                </p>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Tab 3: AI Study Chat (Q&A) */}
            {activeTab === "chat" && (
                <div className="bg-white rounded-2xl border border-slate-200 shadow-sm h-[650px] flex overflow-hidden">
                    {/* Chat Sessions Sidebar */}
                    <div className="w-64 border-r border-slate-100 bg-slate-50/50 p-4 flex flex-col justify-between hidden md:flex">
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                                    Phiên hỏi đáp
                                </h4>
                                <button
                                    onClick={handleCreateChatSession}
                                    className="p-1 rounded-lg text-indigo-600 hover:bg-indigo-50"
                                    title="Tạo phiên chat mới"
                                >
                                    <MessageSquare className="w-4 h-4" />
                                </button>
                            </div>

                            <div className="space-y-1 overflow-y-auto max-h-[500px]">
                                {chatSessions.map((session) => (
                                    <button
                                        key={session.id}
                                        onClick={() => setActiveSessionId(session.id)}
                                        className={`w-full text-left px-3 py-2.5 rounded-xl text-xs transition flex items-center gap-2 ${
                                            activeSessionId === session.id
                                                ? "bg-indigo-600 text-white font-medium shadow-sm"
                                                : "text-slate-600 hover:bg-slate-100"
                                        }`}
                                    >
                                        <MessageSquare className="w-3.5 h-3.5 shrink-0" />
                                        <span className="truncate">{session.title}</span>
                                    </button>
                                ))}
                            </div>
                        </div>

                        <button
                            onClick={handleCreateChatSession}
                            className="w-full py-2 px-3 rounded-xl border border-slate-200 hover:bg-white text-xs font-semibold text-slate-700 transition"
                        >
                            + Tạo phiên mới
                        </button>
                    </div>

                    {/* Chat Messages Pane */}
                    <div className="flex-1 flex flex-col h-full bg-white">
                        {/* Messages Area */}
                        <div className="flex-1 overflow-y-auto p-6 space-y-4">
                            {chatMessages.length > 0 ? (
                                chatMessages.map((msg) => {
                                    const isUser = msg.role === "USER";

                                    return (
                                        <div
                                            key={msg.id}
                                            className={`flex items-start gap-3 ${
                                                isUser ? "flex-row-reverse" : "flex-row"
                                            }`}
                                        >
                                            <div
                                                className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-xs font-bold ${
                                                    isUser
                                                        ? "bg-indigo-600 text-white"
                                                        : "bg-emerald-100 text-emerald-800"
                                                }`}
                                            >
                                                {isUser ? <UserIcon className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                                            </div>

                                            <div
                                                className={`max-w-[75%] p-4 rounded-2xl text-xs md:text-sm leading-relaxed ${
                                                    isUser
                                                        ? "bg-indigo-600 text-white rounded-tr-none shadow-sm"
                                                        : "bg-slate-100 text-slate-800 rounded-tl-none border border-slate-200/60"
                                                }`}
                                            >
                                                {isUser ? (
                                                    <p className="whitespace-pre-wrap">{msg.content}</p>
                                                ) : (
                                                    <div className="prose prose-xs md:prose-sm max-w-none text-slate-800">
                                                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                                            {msg.content}
                                                        </ReactMarkdown>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })
                            ) : (
                                <div className="h-full flex flex-col items-center justify-center text-center p-6 text-slate-400">
                                    <Bot className="w-12 h-12 text-slate-300 mb-2" />
                                    <p className="text-sm font-medium text-slate-600">
                                        Xin chào! Tôi là Trợ lý Gia sư AI.
                                    </p>
                                    <p className="text-xs text-slate-400 mt-1 max-w-sm">
                                        Hãy đặt bất kỳ câu hỏi nào liên quan đến tài liệu học tập này. Tôi sẽ giải thích chi tiết và mạch lạc.
                                    </p>
                                </div>
                            )}

                            {isSendingMessage && (
                                <div className="flex items-start gap-3">
                                    <div className="w-8 h-8 rounded-full bg-emerald-100 text-emerald-800 flex items-center justify-center shrink-0 text-xs">
                                        <Bot className="w-4 h-4" />
                                    </div>
                                    <div className="p-3.5 rounded-2xl rounded-tl-none bg-slate-100 border border-slate-200/60 text-xs text-slate-600 flex items-center gap-2">
                                        <div className="w-3.5 h-3.5 border-2 border-emerald-600/30 border-t-emerald-600 rounded-full animate-spin" />
                                        <span>AI đang phân tích tài liệu và suy nghĩ câu trả lời...</span>
                                    </div>
                                </div>
                            )}

                            <div ref={messagesEndRef} />
                        </div>

                        {/* Chat Input Bar */}
                        <form
                            onSubmit={handleSendMessage}
                            className="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center gap-3"
                        >
                            <input
                                type="text"
                                value={chatInput}
                                onChange={(e) => setChatInput(e.target.value)}
                                placeholder="Hỏi AI bất kỳ điều gì về tài liệu này..."
                                disabled={isSendingMessage}
                                className="flex-1 px-4 py-2.5 rounded-xl border border-slate-300 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-xs md:text-sm text-slate-800 bg-white"
                            />
                            <button
                                type="submit"
                                disabled={isSendingMessage || !chatInput.trim()}
                                className="p-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white transition disabled:opacity-50 shadow-sm"
                            >
                                <Send className="w-4 h-4" />
                            </button>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default MaterialStudyPage;
