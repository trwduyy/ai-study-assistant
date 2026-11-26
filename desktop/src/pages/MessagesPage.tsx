import React, {
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState
} from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
    AlertCircle,
    ArrowLeft,
    Check,
    CheckCheck,
    Clock,
    GraduationCap,
    Lock,
    MessageSquare,
    Plus,
    RefreshCw,
    RotateCcw,
    Search,
    Send,
    UserPlus,
    UserRound,
    Users,
    WifiOff
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import { useWebSocket } from "../contexts/WebSocketContext";
import { ApiRequestError } from "../services/apiClient";
import chatService from "../services/chatService";
import classroomService from "../services/classroomService";
import {
    ChatMessage,
    ClassMember,
    Conversation,
    WsConnectionStatus
} from "../types";
import { Avatar, AvatarFallback } from "../components/ui/avatar";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle
} from "../components/ui/dialog";
import { Input } from "../components/ui/input";
import { EmptyState } from "../components/common/EmptyState";
import { ErrorBanner } from "../components/common/ErrorBanner";
import { LoadingState } from "../components/common/LoadingState";

interface ContactCandidate {
    userId: number;
    fullName: string;
    email: string;
    classNames: string[];
}

function getInitials(name: string): string {
    return (
        name
            .split(" ")
            .filter(Boolean)
            .slice(-2)
            .map((part) => part[0])
            .join("")
            .toUpperCase() || "ND"
    );
}

function formatMessageTime(isoDate: string): string {
    const date = new Date(isoDate);
    if (Number.isNaN(date.getTime())) {
        return "";
    }
    return date.toLocaleTimeString("vi-VN", {
        hour: "2-digit",
        minute: "2-digit"
    });
}

function formatConversationTime(isoDate: string): string {
    const date = new Date(isoDate);
    if (Number.isNaN(date.getTime())) {
        return "";
    }
    const now = new Date();
    const isSameDay =
        date.getDate() === now.getDate() &&
        date.getMonth() === now.getMonth() &&
        date.getFullYear() === now.getFullYear();

    if (isSameDay) {
        return formatMessageTime(isoDate);
    }
    return date.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit"
    });
}

function isAccessForbiddenError(err: unknown): boolean {
    if (err instanceof ApiRequestError) {
        return (
            err.status === 403 ||
            err.code === "NOT_CONVERSATION_MEMBER" ||
            err.code === "NOT_CLASS_MEMBER" ||
            err.code === "FORBIDDEN"
        );
    }
    return false;
}

function mergeMessagesChronologically(
    existing: ChatMessage[],
    incoming: ChatMessage[]
): ChatMessage[] {
    const result = [...existing];

    for (const msg of incoming) {
        const matchIndex = result.findIndex(
            (item) =>
                (msg.id > 0 && item.id === msg.id) ||
                (Boolean(msg.clientMessageId) &&
                    Boolean(item.clientMessageId) &&
                    item.clientMessageId === msg.clientMessageId)
        );

        if (matchIndex >= 0) {
            result[matchIndex] = msg;
        } else {
            result.push(msg);
        }
    }

    return result.sort((a, b) => {
        const aIsOptimistic = a.id < 0;
        const bIsOptimistic = b.id < 0;
        if (aIsOptimistic && !bIsOptimistic) return 1;
        if (!aIsOptimistic && bIsOptimistic) return -1;
        if (!aIsOptimistic && !bIsOptimistic) {
            return a.id - b.id;
        }
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    });
}

export const MessagesPage: React.FC = () => {
    const { conversationId } = useParams<{ conversationId?: string }>();
    const navigate = useNavigate();
    const { user } = useAuth();
    const {
        status: wsStatus,
        subscribeConversation,
        sendConversationMessage
    } = useWebSocket();

    const selectedId = conversationId ? Number(conversationId) : null;
    const validSelectedId =
        selectedId !== null && !Number.isNaN(selectedId) && selectedId > 0 ? selectedId : null;

    // Conversations list state
    const [conversations, setConversations] = useState<Conversation[]>([]);
    const [isConversationsLoading, setIsConversationsLoading] = useState<boolean>(true);
    const [conversationsError, setConversationsError] = useState<string | null>(null);
    const [searchKeyword, setSearchKeyword] = useState<string>("");
    const [typeFilter, setTypeFilter] = useState<"ALL" | "CLASSROOM" | "DIRECT">("ALL");

    // Active conversation messages state
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [nextCursor, setNextCursor] = useState<string | null>(null);
    const [isMessagesLoading, setIsMessagesLoading] = useState<boolean>(false);
    const [isLoadingOlder, setIsLoadingOlder] = useState<boolean>(false);
    const [messagesError, setMessagesError] = useState<string | null>(null);
    const [isAccessRevoked, setIsAccessRevoked] = useState<boolean>(false);
    const [revokedReason, setRevokedReason] = useState<string>("");

    // Compose input state
    const [draftText, setDraftText] = useState<string>("");

    // New Direct Message Dialog state
    const [isNewChatOpen, setIsNewChatOpen] = useState<boolean>(false);
    const [contacts, setContacts] = useState<ContactCandidate[]>([]);
    const [isLoadingContacts, setIsLoadingContacts] = useState<boolean>(false);
    const [contactSearch, setContactSearch] = useState<string>("");
    const [manualUserId, setManualUserId] = useState<string>("");
    const [isOpeningDirect, setIsOpeningDirect] = useState<boolean>(false);

    // Scroll & Read debounce refs
    const messagesContainerRef = useRef<HTMLDivElement | null>(null);
    const scrollPreserveRef = useRef<{
        active: boolean;
        prevHeight: number;
        prevTop: number;
    }>({ active: false, prevHeight: 0, prevTop: 0 });
    const shouldScrollBottomRef = useRef<boolean>(false);
    const readDebounceTimerRef = useRef<number | null>(null);
    const lastMarkedReadIdRef = useRef<number>(0);
    const prevWsStatusRef = useRef<WsConnectionStatus>(wsStatus);
    const optimisticTimeoutRef = useRef<Map<string, number>>(new Map());

    const activeConversation = useMemo(
        () => conversations.find((c) => c.id === validSelectedId) ?? null,
        [conversations, validSelectedId]
    );

    const filteredConversations = useMemo(() => {
        const keyword = searchKeyword.trim().toLowerCase();
        return conversations.filter((conv) => {
            if (typeFilter !== "ALL" && conv.type !== typeFilter) {
                return false;
            }
            if (!keyword) {
                return true;
            }
            return (
                conv.title.toLowerCase().includes(keyword) ||
                (conv.lastMessage?.content.toLowerCase().includes(keyword) ?? false)
            );
        });
    }, [conversations, searchKeyword, typeFilter]);

    // Fetch conversations list
    const fetchConversations = useCallback(async (showSpinner = true): Promise<void> => {
        try {
            if (showSpinner) {
                setIsConversationsLoading(true);
            }
            const list = await chatService.getConversations();
            setConversations(list);
            setConversationsError(null);
        } catch (err: unknown) {
            setConversationsError(
                err instanceof Error ? err.message : "Không thể tải danh sách cuộc trò chuyện."
            );
        } finally {
            if (showSpinner) {
                setIsConversationsLoading(false);
            }
        }
    }, []);

    useEffect(() => {
        void fetchConversations(true);
    }, [fetchConversations]);

    // Debounced markAsRead helper (500ms)
    const scheduleMarkAsRead = useCallback(
        (convId: number, latestMessageId: number) => {
            if (latestMessageId <= 0 || latestMessageId <= lastMarkedReadIdRef.current) {
                return;
            }

            if (readDebounceTimerRef.current !== null) {
                window.clearTimeout(readDebounceTimerRef.current);
            }

            readDebounceTimerRef.current = window.setTimeout(() => {
                void (async () => {
                    try {
                        await chatService.markAsRead(convId, latestMessageId);
                        lastMarkedReadIdRef.current = Math.max(
                            lastMarkedReadIdRef.current,
                            latestMessageId
                        );
                        setConversations((prev) =>
                            prev.map((c) => (c.id === convId ? { ...c, unreadCount: 0 } : c))
                        );
                    } catch (err: unknown) {
                        if (isAccessForbiddenError(err)) {
                            setIsAccessRevoked(true);
                            setRevokedReason(
                                err instanceof Error
                                    ? err.message
                                    : "Bạn không còn quyền truy cập cuộc trò chuyện này."
                            );
                        }
                    }
                })();
            }, 500);
        },
        []
    );

    useEffect(() => {
        return () => {
            if (readDebounceTimerRef.current !== null) {
                window.clearTimeout(readDebounceTimerRef.current);
            }
        };
    }, []);

    // Update conversation preview in left column
    const updateConversationPreview = useCallback(
        (convId: number, msg: ChatMessage, incrementUnread: boolean) => {
            setConversations((prev) => {
                const updated = prev.map((c) => {
                    if (c.id !== convId) {
                        return c;
                    }
                    return {
                        ...c,
                        unreadCount: incrementUnread ? c.unreadCount + 1 : 0,
                        lastMessage: {
                            id: msg.id,
                            senderId: msg.senderId,
                            senderName: msg.senderName,
                            content: msg.content,
                            createdAt: msg.createdAt
                        },
                        updatedAt: msg.createdAt
                    };
                });

                return [...updated].sort(
                    (a, b) =>
                        new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime() ||
                        b.id - a.id
                );
            });
        },
        []
    );

    // Load initial messages for selected conversation
    const loadLatestMessages = useCallback(
        async (convId: number, isCatchUp = false): Promise<void> => {
            try {
                if (!isCatchUp) {
                    setIsMessagesLoading(true);
                    setMessagesError(null);
                    setIsAccessRevoked(false);
                    setRevokedReason("");
                    lastMarkedReadIdRef.current = 0;
                }

                const page = await chatService.getMessages(convId, null, 30);
                // Backend returns newest first (ORDER BY m.id DESC)
                const chronological = [...page.items].reverse();

                if (isCatchUp) {
                    setMessages((prev) => mergeMessagesChronologically(prev, chronological));
                } else {
                    shouldScrollBottomRef.current = true;
                    setMessages(chronological);
                    setNextCursor(page.nextCursor);
                }

                const latestServerMsg = chronological[chronological.length - 1];
                if (latestServerMsg && latestServerMsg.id > 0) {
                    scheduleMarkAsRead(convId, latestServerMsg.id);
                }
            } catch (err: unknown) {
                if (isAccessForbiddenError(err)) {
                    setIsAccessRevoked(true);
                    setRevokedReason(
                        err instanceof Error
                            ? err.message
                            : "Bạn đã rời lớp hoặc bị gỡ khỏi cuộc trò chuyện này nên không thể tiếp tục nhắn tin."
                    );
                    if (!isCatchUp) {
                        setMessages([]);
                    }
                } else if (!isCatchUp) {
                    setMessagesError(
                        err instanceof Error ? err.message : "Không thể tải lịch sử tin nhắn."
                    );
                }
            } finally {
                if (!isCatchUp) {
                    setIsMessagesLoading(false);
                }
            }
        },
        [scheduleMarkAsRead]
    );

    useEffect(() => {
        // Clear pending optimistic timers when switching conversations
        optimisticTimeoutRef.current.forEach((timerId) => window.clearTimeout(timerId));
        optimisticTimeoutRef.current.clear();

        if (!validSelectedId) {
            setMessages([]);
            setNextCursor(null);
            setMessagesError(null);
            setIsAccessRevoked(false);
            return;
        }

        void loadLatestMessages(validSelectedId, false);
    }, [validSelectedId, loadLatestMessages]);

    // Load older messages by cursor while preserving scroll position
    const handleLoadOlder = async (): Promise<void> => {
        if (!validSelectedId || !nextCursor || isLoadingOlder) {
            return;
        }

        const container = messagesContainerRef.current;
        if (container) {
            scrollPreserveRef.current = {
                active: true,
                prevHeight: container.scrollHeight,
                prevTop: container.scrollTop
            };
        }

        try {
            setIsLoadingOlder(true);
            const page = await chatService.getMessages(validSelectedId, nextCursor, 30);
            const olderChronological = [...page.items].reverse();

            setMessages((prev) => mergeMessagesChronologically(prev, olderChronological));
            setNextCursor(page.nextCursor);
        } catch (err: unknown) {
            scrollPreserveRef.current.active = false;
            if (isAccessForbiddenError(err)) {
                setIsAccessRevoked(true);
                setRevokedReason(
                    err instanceof Error
                        ? err.message
                        : "Bạn không còn quyền xem cuộc trò chuyện này."
                );
            } else {
                toast.error(
                    err instanceof Error ? err.message : "Không thể tải thêm tin nhắn cũ hơn."
                );
            }
        } finally {
            setIsLoadingOlder(false);
        }
    };

    // Preserve scroll position after prepending older messages or scroll to bottom for new ones
    useLayoutEffect(() => {
        const container = messagesContainerRef.current;
        if (!container) {
            return;
        }

        if (scrollPreserveRef.current.active) {
            const { prevHeight, prevTop } = scrollPreserveRef.current;
            const heightDiff = container.scrollHeight - prevHeight;
            container.scrollTop = prevTop + heightDiff;
            scrollPreserveRef.current.active = false;
            return;
        }

        if (shouldScrollBottomRef.current) {
            container.scrollTop = container.scrollHeight;
            shouldScrollBottomRef.current = false;
        }
    }, [messages]);

    // STOMP subscription for active conversation
    useEffect(() => {
        if (!validSelectedId || isAccessRevoked) {
            return;
        }

        return subscribeConversation(validSelectedId, (incoming) => {
            if (incoming.clientMessageId) {
                const pendingTimer = optimisticTimeoutRef.current.get(incoming.clientMessageId);
                if (pendingTimer !== undefined) {
                    window.clearTimeout(pendingTimer);
                    optimisticTimeoutRef.current.delete(incoming.clientMessageId);
                }
            }

            const container = messagesContainerRef.current;
            const isNearBottom = container
                ? container.scrollHeight - container.scrollTop - container.clientHeight < 140
                : true;
            if (isNearBottom || incoming.senderId === user?.id) {
                shouldScrollBottomRef.current = true;
            }

            setMessages((prev) => mergeMessagesChronologically(prev, [incoming]));
            updateConversationPreview(validSelectedId, incoming, false);
            if (incoming.id > 0) {
                scheduleMarkAsRead(validSelectedId, incoming.id);
            }
        });
    }, [
        isAccessRevoked,
        scheduleMarkAsRead,
        subscribeConversation,
        updateConversationPreview,
        user?.id,
        validSelectedId
    ]);

    // Auto catch-up when WebSocket reconnects
    useEffect(() => {
        const previous = prevWsStatusRef.current;
        prevWsStatusRef.current = wsStatus;

        const cameBackOnline =
            wsStatus === "CONNECTED" &&
            (previous === "RECONNECTING" ||
                previous === "ERROR" ||
                previous === "DISCONNECTED");

        if (cameBackOnline) {
            void fetchConversations(false);
            if (validSelectedId && !isAccessRevoked) {
                void loadLatestMessages(validSelectedId, true);
            }
        }
    }, [fetchConversations, isAccessRevoked, loadLatestMessages, validSelectedId, wsStatus]);

    // Send message via REST fallback
    const sendViaRestFallback = useCallback(
        async (convId: number, clientMessageId: string, content: string): Promise<void> => {
            try {
                const saved = await chatService.sendMessageRest(convId, {
                    clientMessageId,
                    content
                });

                const pendingTimer = optimisticTimeoutRef.current.get(clientMessageId);
                if (pendingTimer !== undefined) {
                    window.clearTimeout(pendingTimer);
                    optimisticTimeoutRef.current.delete(clientMessageId);
                }

                setMessages((prev) => mergeMessagesChronologically(prev, [saved]));
                updateConversationPreview(convId, saved, false);
                if (saved.id > 0) {
                    scheduleMarkAsRead(convId, saved.id);
                }
            } catch (err: unknown) {
                if (isAccessForbiddenError(err)) {
                    setIsAccessRevoked(true);
                    setRevokedReason(
                        err instanceof Error
                            ? err.message
                            : "Bạn đã bị thu hồi quyền gửi tin nhắn trong cuộc trò chuyện này."
                    );
                }
                setMessages((prev) =>
                    prev.map((m) =>
                        m.clientMessageId === clientMessageId ? { ...m, status: "FAILED" } : m
                    )
                );
                toast.error(err instanceof Error ? err.message : "Gửi tin nhắn thất bại.");
            }
        },
        [scheduleMarkAsRead, updateConversationPreview]
    );

    const handleSendMessage = async (e?: React.FormEvent): Promise<void> => {
        if (e) {
            e.preventDefault();
        }
        if (!validSelectedId || isAccessRevoked) {
            return;
        }
        const trimmed = draftText.trim();
        if (!trimmed) {
            return;
        }

        const clientMessageId = crypto.randomUUID();
        const optimisticMsg: ChatMessage = {
            id: -Date.now(),
            conversationId: validSelectedId,
            senderId: user?.id ?? 0,
            senderName: user?.fullName ?? "Bạn",
            content: trimmed,
            status: "SENDING",
            clientMessageId,
            createdAt: new Date().toISOString()
        };

        setDraftText("");
        shouldScrollBottomRef.current = true;
        setMessages((prev) => mergeMessagesChronologically(prev, [optimisticMsg]));

        const sentViaStomp =
            wsStatus === "CONNECTED" &&
            sendConversationMessage(validSelectedId, {
                clientMessageId,
                content: trimmed
            });

        if (sentViaStomp) {
            // If STOMP echo doesn't arrive within 4s (e.g. silent drop), safely retry via idempotent REST
            const timerId = window.setTimeout(() => {
                optimisticTimeoutRef.current.delete(clientMessageId);
                void sendViaRestFallback(validSelectedId, clientMessageId, trimmed);
            }, 4000);
            optimisticTimeoutRef.current.set(clientMessageId, timerId);
        } else {
            await sendViaRestFallback(validSelectedId, clientMessageId, trimmed);
        }
    };

    const handleRetryFailedMessage = async (msg: ChatMessage): Promise<void> => {
        if (!validSelectedId || !msg.clientMessageId || isAccessRevoked) {
            return;
        }
        setMessages((prev) =>
            prev.map((item) =>
                item.clientMessageId === msg.clientMessageId
                    ? { ...item, status: "SENDING" }
                    : item
            )
        );
        await sendViaRestFallback(validSelectedId, msg.clientMessageId, msg.content);
    };

    // Load contacts from active classrooms when opening "Tin nhắn mới" dialog
    const handleOpenNewChatDialog = async (): Promise<void> => {
        setIsNewChatOpen(true);
        setContactSearch("");
        setManualUserId("");

        try {
            setIsLoadingContacts(true);
            const classesRes = await classroomService.getMyClassrooms({ page: 0, size: 30 });
            const memberResponses = await Promise.all(
                classesRes.items.map(async (cls) => {
                    try {
                        const res = await classroomService.getMembers(cls.id, undefined, 0, 50);
                        return { className: cls.name, members: res.items };
                    } catch {
                        return { className: cls.name, members: [] as ClassMember[] };
                    }
                })
            );

            const byUserId = new Map<number, ContactCandidate>();
            for (const entry of memberResponses) {
                for (const m of entry.members) {
                    if (m.userId === user?.id) {
                        continue;
                    }
                    const existing = byUserId.get(m.userId);
                    if (existing) {
                        if (!existing.classNames.includes(entry.className)) {
                            existing.classNames.push(entry.className);
                        }
                    } else {
                        byUserId.set(m.userId, {
                            userId: m.userId,
                            fullName: m.fullName,
                            email: m.email,
                            classNames: [entry.className]
                        });
                    }
                }
            }
            setContacts(Array.from(byUserId.values()));
        } catch {
            // Contacts list is optional; manual ID input remains available
        } finally {
            setIsLoadingContacts(false);
        }
    };

    const handleStartDirectChat = async (targetUserId: number): Promise<void> => {
        if (!targetUserId || targetUserId <= 0) {
            toast.error("Vui lòng nhập ID người dùng hợp lệ.");
            return;
        }
        if (targetUserId === user?.id) {
            toast.error("Không thể mở hội thoại 1-1 với chính bạn.");
            return;
        }

        try {
            setIsOpeningDirect(true);
            const conv = await chatService.openOrGetDirectConversation(targetUserId);
            setConversations((prev) => {
                const exists = prev.some((c) => c.id === conv.id);
                if (exists) {
                    return prev.map((c) => (c.id === conv.id ? conv : c));
                }
                return [conv, ...prev];
            });
            setIsNewChatOpen(false);
            navigate(`/messages/${conv.id}`);
        } catch (err: unknown) {
            toast.error(
                err instanceof Error ? err.message : "Không thể mở cuộc trò chuyện 1-1."
            );
        } finally {
            setIsOpeningDirect(false);
        }
    };

    const filteredContacts = useMemo(() => {
        const keyword = contactSearch.trim().toLowerCase();
        if (!keyword) {
            return contacts;
        }
        return contacts.filter(
            (c) =>
                c.fullName.toLowerCase().includes(keyword) ||
                c.email.toLowerCase().includes(keyword) ||
                c.classNames.some((name) => name.toLowerCase().includes(keyword))
        );
    }, [contactSearch, contacts]);

    return (
        <div className="flex h-[calc(100vh-9.5rem)] min-h-[540px] overflow-hidden rounded-xl border border-border bg-surface shadow-sm">
            {/* LEFT COLUMN: Conversations List */}
            <aside
                className={`w-full md:w-80 lg:w-96 flex-col border-r border-border bg-slate-50/40 shrink-0 ${
                    validSelectedId ? "hidden md:flex" : "flex"
                }`}
            >
                <div className="p-4 border-b border-border space-y-3 bg-surface">
                    <div className="flex items-center justify-between gap-2">
                        <div>
                            <h1 className="text-base font-bold text-foreground">Hội thoại</h1>
                            <p className="text-xs text-muted-foreground">
                                Nhóm lớp học và tin nhắn riêng
                            </p>
                        </div>
                        <Button
                            size="sm"
                            onClick={() => void handleOpenNewChatDialog()}
                            className="h-8 gap-1.5 text-xs"
                        >
                            <Plus className="h-3.5 w-3.5" />
                            <span>Tin nhắn mới</span>
                        </Button>
                    </div>

                    <div className="relative">
                        <Search className="h-4 w-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                        <Input
                            value={searchKeyword}
                            onChange={(e) => setSearchKeyword(e.target.value)}
                            placeholder="Tìm theo tên phòng hoặc tin nhắn..."
                            className="pl-9 h-9 text-xs"
                        />
                    </div>

                    <div className="grid grid-cols-3 rounded-lg bg-muted p-1 text-xs font-medium">
                        {(
                            [
                                { key: "ALL", label: "Tất cả" },
                                { key: "CLASSROOM", label: "Phòng lớp" },
                                { key: "DIRECT", label: "Cá nhân" }
                            ] as const
                        ).map((tab) => (
                            <button
                                key={tab.key}
                                type="button"
                                onClick={() => setTypeFilter(tab.key)}
                                className={`rounded-md py-1 transition-colors ${
                                    typeFilter === tab.key
                                        ? "bg-surface text-foreground shadow-sm font-semibold"
                                        : "text-muted-foreground hover:text-foreground"
                                }`}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Conversation items */}
                <div className="flex-1 overflow-y-auto divide-y divide-border/60">
                    {isConversationsLoading ? (
                        <div className="p-6">
                            <LoadingState label="Đang tải danh sách hội thoại..." />
                        </div>
                    ) : conversationsError ? (
                        <div className="p-4">
                            <ErrorBanner
                                title="Lỗi tải hội thoại"
                                message={conversationsError}
                                onRetry={() => void fetchConversations(true)}
                            />
                        </div>
                    ) : filteredConversations.length === 0 ? (
                        <div className="p-6 text-center space-y-2">
                            <MessageSquare className="h-9 w-9 text-slate-300 mx-auto" />
                            <p className="text-sm font-semibold text-slate-700">
                                {searchKeyword
                                    ? "Không tìm thấy cuộc trò chuyện nào"
                                    : "Chưa có cuộc trò chuyện"}
                            </p>
                            <p className="text-xs text-muted-foreground">
                                Tham gia lớp học để vào phòng chat lớp hoặc bấm "Tin nhắn mới" để nhắn 1-1.
                            </p>
                        </div>
                    ) : (
                        filteredConversations.map((conv) => {
                            const isSelected = conv.id === validSelectedId;
                            const isClassroom = conv.type === "CLASSROOM";
                            const hasUnread = conv.unreadCount > 0;

                            return (
                                <button
                                    key={conv.id}
                                    type="button"
                                    onClick={() => navigate(`/messages/${conv.id}`)}
                                    className={`w-full p-3.5 text-left transition-colors flex items-start gap-3 hover:bg-slate-100/80 ${
                                        isSelected
                                            ? "bg-blue-50/80 border-l-4 border-l-primary"
                                            : "bg-transparent border-l-4 border-l-transparent"
                                    }`}
                                >
                                    <div
                                        className={`h-10 w-10 rounded-full flex items-center justify-center shrink-0 ${
                                            isClassroom
                                                ? "bg-blue-100 text-primary"
                                                : "bg-teal-100 text-teal-700"
                                        }`}
                                    >
                                        {isClassroom ? (
                                            <GraduationCap className="h-5 w-5" />
                                        ) : (
                                            <UserRound className="h-5 w-5" />
                                        )}
                                    </div>

                                    <div className="min-w-0 flex-1 space-y-1">
                                        <div className="flex items-center justify-between gap-2">
                                            <span
                                                className={`text-sm truncate ${
                                                    hasUnread || isSelected
                                                        ? "font-bold text-slate-900"
                                                        : "font-semibold text-slate-800"
                                                }`}
                                            >
                                                {conv.title}
                                            </span>
                                            <span className="text-[11px] text-muted-foreground shrink-0">
                                                {formatConversationTime(conv.updatedAt)}
                                            </span>
                                        </div>

                                        <div className="flex items-center gap-1.5">
                                            <Badge
                                                variant={isClassroom ? "default" : "secondary"}
                                                className="text-[10px] px-1.5 py-0 font-medium"
                                            >
                                                {isClassroom ? "Phòng lớp" : "Tin nhắn riêng"}
                                            </Badge>
                                        </div>

                                        <div className="flex items-center justify-between gap-2">
                                            <p
                                                className={`text-xs truncate ${
                                                    hasUnread
                                                        ? "font-semibold text-slate-800"
                                                        : "text-muted-foreground"
                                                }`}
                                            >
                                                {conv.lastMessage
                                                    ? `${conv.lastMessage.senderId === user?.id ? "Bạn" : conv.lastMessage.senderName}: ${conv.lastMessage.content}`
                                                    : "Chưa có tin nhắn nào"}
                                            </p>
                                            {hasUnread && (
                                                <span className="h-5 min-w-5 px-1.5 rounded-full bg-primary text-white text-[10px] font-bold flex items-center justify-center shrink-0">
                                                    {conv.unreadCount > 99 ? "99+" : conv.unreadCount}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </button>
                            );
                        })
                    )}
                </div>
            </aside>

            {/* RIGHT COLUMN: Conversation Thread */}
            <section
                className={`flex-1 flex-col min-w-0 bg-surface ${
                    validSelectedId ? "flex" : "hidden md:flex"
                }`}
            >
                {!validSelectedId ? (
                    <div className="flex-1 flex items-center justify-center p-8">
                        <EmptyState
                            icon={<MessageSquare className="h-12 w-12 text-slate-300" />}
                            title="Chọn một cuộc trò chuyện để bắt đầu"
                            description="Chọn phòng chat lớp học hoặc hội thoại cá nhân ở cột bên trái, hoặc tạo tin nhắn mới."
                            action={
                                <Button
                                    onClick={() => void handleOpenNewChatDialog()}
                                    className="gap-2 text-xs"
                                >
                                    <UserPlus className="h-4 w-4" />
                                    <span>Mở tin nhắn mới</span>
                                </Button>
                            }
                        />
                    </div>
                ) : (
                    <>
                        {/* Thread Header */}
                        <div className="h-16 px-4 sm:px-6 border-b border-border flex items-center justify-between gap-3 shrink-0 bg-surface">
                            <div className="flex items-center gap-3 min-w-0">
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    onClick={() => navigate("/messages")}
                                    className="md:hidden h-8 w-8 shrink-0"
                                    aria-label="Quay lại danh sách hội thoại"
                                >
                                    <ArrowLeft className="h-4 w-4" />
                                </Button>

                                <div
                                    className={`h-9 w-9 rounded-full flex items-center justify-center shrink-0 ${
                                        activeConversation?.type === "CLASSROOM"
                                            ? "bg-blue-100 text-primary"
                                            : "bg-teal-100 text-teal-700"
                                    }`}
                                >
                                    {activeConversation?.type === "CLASSROOM" ? (
                                        <Users className="h-4 w-4" />
                                    ) : (
                                        <UserRound className="h-4 w-4" />
                                    )}
                                </div>

                                <div className="min-w-0">
                                    <div className="flex items-center gap-2">
                                        <h2 className="text-sm font-bold text-slate-900 truncate">
                                            {activeConversation?.title || `Cuộc trò chuyện #${validSelectedId}`}
                                        </h2>
                                        {activeConversation && (
                                            <Badge
                                                variant={
                                                    activeConversation.type === "CLASSROOM"
                                                        ? "default"
                                                        : "secondary"
                                                }
                                                className="text-[10px] hidden sm:inline-flex"
                                            >
                                                {activeConversation.type === "CLASSROOM"
                                                    ? "Phòng lớp"
                                                    : "Tin nhắn riêng"}
                                            </Badge>
                                        )}
                                    </div>
                                    <p className="text-[11px] text-muted-foreground">
                                        {isAccessRevoked
                                            ? "Quyền truy cập đã bị thu hồi"
                                            : wsStatus === "CONNECTED"
                                            ? "Đang đồng bộ thời gian thực"
                                            : "Mất kết nối trực tiếp — tự động gửi dự phòng qua REST"}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-center gap-2 shrink-0">
                                {activeConversation?.type === "CLASSROOM" &&
                                    activeConversation.classId &&
                                    !isAccessRevoked && (
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() =>
                                                navigate(`/classes/${activeConversation.classId}`)
                                            }
                                            className="text-xs h-8 hidden sm:inline-flex"
                                        >
                                            Mở lớp học
                                        </Button>
                                    )}
                                <Button
                                    size="icon"
                                    variant="ghost"
                                    onClick={() => void loadLatestMessages(validSelectedId, false)}
                                    disabled={isMessagesLoading}
                                    title="Làm mới tin nhắn"
                                    aria-label="Làm mới tin nhắn"
                                    className="h-8 w-8"
                                >
                                    <RefreshCw
                                        className={`h-4 w-4 ${isMessagesLoading ? "animate-spin" : ""}`}
                                    />
                                </Button>
                            </div>
                        </div>

                        {/* Reconnect / Offline notice */}
                        {wsStatus !== "CONNECTED" && !isAccessRevoked && (
                            <div className="px-4 py-2 bg-amber-50 border-b border-amber-200 text-xs text-amber-800 flex items-center gap-2">
                                <WifiOff className="h-3.5 w-3.5 shrink-0" />
                                <span>
                                    Kết nối trực tiếp đang gián đoạn. Tin nhắn gửi đi sẽ dùng kênh dự phòng REST và tự đồng bộ lại khi có mạng.
                                </span>
                            </div>
                        )}

                        {/* 403 Access Revoked Banner */}
                        {isAccessRevoked && (
                            <div className="m-4 p-4 rounded-xl border border-rose-200 bg-rose-50 text-rose-900 flex items-start gap-3 text-sm">
                                <Lock className="h-5 w-5 text-rose-600 shrink-0 mt-0.5" />
                                <div className="space-y-1">
                                    <p className="font-bold">Không thể truy cập cuộc trò chuyện</p>
                                    <p className="text-xs text-rose-800 leading-relaxed">
                                        {revokedReason ||
                                            "Bạn đã rời lớp học hoặc bị giảng viên gỡ khỏi lớp nên khung chat đã bị khóa."}
                                    </p>
                                </div>
                            </div>
                        )}

                        {/* Messages Scroll Container */}
                        <div
                            ref={messagesContainerRef}
                            className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-3 bg-slate-50/50"
                        >
                            {isMessagesLoading ? (
                                <LoadingState label="Đang tải lịch sử tin nhắn..." />
                            ) : messagesError ? (
                                <ErrorBanner
                                    title="Lỗi tải tin nhắn"
                                    message={messagesError}
                                    onRetry={() => void loadLatestMessages(validSelectedId, false)}
                                />
                            ) : (
                                <>
                                    {/* Load Older Button (Cursor Pagination) */}
                                    {nextCursor && !isAccessRevoked && (
                                        <div className="flex justify-center pb-2">
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                onClick={() => void handleLoadOlder()}
                                                disabled={isLoadingOlder}
                                                className="text-xs h-8 bg-surface shadow-sm"
                                            >
                                                {isLoadingOlder
                                                    ? "Đang tải tin nhắn cũ..."
                                                    : "Tải tin nhắn cũ hơn"}
                                            </Button>
                                        </div>
                                    )}

                                    {messages.length === 0 && !isAccessRevoked ? (
                                        <div className="py-16 text-center space-y-2">
                                            <MessageSquare className="h-10 w-10 text-slate-300 mx-auto" />
                                            <p className="text-sm font-medium text-slate-700">
                                                Chưa có tin nhắn nào trong cuộc trò chuyện này
                                            </p>
                                            <p className="text-xs text-muted-foreground">
                                                Hãy gửi lời chào đầu tiên bên dưới.
                                            </p>
                                        </div>
                                    ) : (
                                        messages.map((msg, index) => {
                                            const isMine = msg.senderId === user?.id;
                                            const prevMsg = index > 0 ? messages[index - 1] : null;
                                            const showSenderName =
                                                !isMine &&
                                                (!prevMsg || prevMsg.senderId !== msg.senderId);

                                            return (
                                                <div
                                                    key={msg.clientMessageId || msg.id}
                                                    className={`flex flex-col ${
                                                        isMine ? "items-end" : "items-start"
                                                    }`}
                                                >
                                                    {showSenderName && (
                                                        <span className="text-[11px] font-semibold text-slate-600 ml-10 mb-1">
                                                            {msg.senderName}
                                                        </span>
                                                    )}

                                                    <div
                                                        className={`flex items-end gap-2 max-w-[80%] sm:max-w-[70%] ${
                                                            isMine ? "flex-row-reverse" : "flex-row"
                                                        }`}
                                                    >
                                                        {!isMine && (
                                                            <Avatar className="h-7 w-7 shrink-0">
                                                                <AvatarFallback className="text-[10px]">
                                                                    {getInitials(msg.senderName)}
                                                                </AvatarFallback>
                                                            </Avatar>
                                                        )}

                                                        <div
                                                            className={`rounded-2xl px-3.5 py-2 text-sm shadow-sm ${
                                                                isMine
                                                                    ? msg.status === "FAILED"
                                                                        ? "bg-rose-600 text-white rounded-br-sm"
                                                                        : "bg-primary text-white rounded-br-sm"
                                                                    : "bg-surface border border-border text-slate-800 rounded-bl-sm"
                                                            }`}
                                                        >
                                                            <p className="whitespace-pre-wrap break-words leading-relaxed">
                                                                {msg.content}
                                                            </p>

                                                            <div
                                                                className={`mt-1 flex items-center justify-end gap-1.5 text-[10px] ${
                                                                    isMine
                                                                        ? "text-blue-100"
                                                                        : "text-muted-foreground"
                                                                }`}
                                                            >
                                                                <span>
                                                                    {formatMessageTime(msg.createdAt)}
                                                                </span>

                                                                {isMine && (
                                                                    <span className="inline-flex items-center gap-0.5">
                                                                        {msg.status === "SENDING" && (
                                                                            <>
                                                                                <Clock className="h-2.5 w-2.5 animate-pulse" />
                                                                                <span>Đang gửi</span>
                                                                            </>
                                                                        )}
                                                                        {(msg.status === "SENT" ||
                                                                            msg.status ===
                                                                                "DELIVERED") && (
                                                                            <>
                                                                                <Check className="h-2.5 w-2.5" />
                                                                                <span>Đã gửi</span>
                                                                            </>
                                                                        )}
                                                                        {msg.status === "READ" && (
                                                                            <>
                                                                                <CheckCheck className="h-2.5 w-2.5" />
                                                                                <span>Đã xem</span>
                                                                            </>
                                                                        )}
                                                                        {msg.status === "FAILED" && (
                                                                            <>
                                                                                <AlertCircle className="h-2.5 w-2.5" />
                                                                                <span>Gửi lỗi</span>
                                                                            </>
                                                                        )}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>

                                                    {isMine && msg.status === "FAILED" && (
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                void handleRetryFailedMessage(msg)
                                                            }
                                                            className="mt-1 inline-flex items-center gap-1 text-[11px] font-semibold text-rose-600 hover:underline"
                                                        >
                                                            <RotateCcw className="h-3 w-3" />
                                                            <span>Gửi lại</span>
                                                        </button>
                                                    )}
                                                </div>
                                            );
                                        })
                                    )}
                                </>
                            )}
                        </div>

                        {/* Message Input Footer */}
                        <form
                            onSubmit={(e) => void handleSendMessage(e)}
                            className="p-3 sm:p-4 border-t border-border bg-surface flex items-end gap-2.5 shrink-0"
                        >
                            <textarea
                                value={draftText}
                                onChange={(e) => setDraftText(e.target.value)}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter" && !e.shiftKey) {
                                        e.preventDefault();
                                        void handleSendMessage();
                                    }
                                }}
                                placeholder={
                                    isAccessRevoked
                                        ? "Khung nhập tin nhắn đã bị khóa do không còn quyền thành viên"
                                        : "Nhập tin nhắn... (Enter để gửi, Shift+Enter xuống dòng)"
                                }
                                rows={1}
                                disabled={isAccessRevoked}
                                className="flex-1 resize-none rounded-xl border border-border bg-slate-50/60 px-3.5 py-2.5 text-sm text-foreground placeholder:text-muted-foreground focus:bg-surface focus:outline-none focus:ring-2 focus:ring-primary/25 focus:border-primary disabled:cursor-not-allowed disabled:opacity-60 max-h-32"
                            />
                            <Button
                                type="submit"
                                disabled={isAccessRevoked || !draftText.trim()}
                                className="h-10 px-4 gap-1.5 shrink-0"
                            >
                                <Send className="h-4 w-4" />
                                <span className="hidden sm:inline">Gửi</span>
                            </Button>
                        </form>
                    </>
                )}
            </section>

            {/* New Direct Chat Modal */}
            <Dialog open={isNewChatOpen} onOpenChange={setIsNewChatOpen}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <UserPlus className="h-5 w-5 text-primary" />
                            <span>Bắt đầu tin nhắn riêng 1-1</span>
                        </DialogTitle>
                        <DialogDescription>
                            Chọn thành viên từ các lớp học của bạn hoặc nhập trực tiếp mã ID người dùng.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4">
                        {/* Quick pick from classroom members */}
                        <div className="space-y-2">
                            <label className="text-xs font-semibold text-slate-700">
                                Chọn nhanh thành viên cùng lớp
                            </label>
                            <div className="relative">
                                <Search className="h-3.5 w-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                                <Input
                                    value={contactSearch}
                                    onChange={(e) => setContactSearch(e.target.value)}
                                    placeholder="Lọc theo tên, email hoặc tên lớp..."
                                    className="pl-8 h-8 text-xs"
                                    disabled={isOpeningDirect}
                                />
                            </div>

                            <div className="max-h-48 overflow-y-auto rounded-lg border border-border divide-y divide-border/70">
                                {isLoadingContacts ? (
                                    <div className="p-4 text-center text-xs text-muted-foreground">
                                        Đang tải danh sách thành viên lớp...
                                    </div>
                                ) : filteredContacts.length === 0 ? (
                                    <div className="p-4 text-center text-xs text-muted-foreground">
                                        Không tìm thấy thành viên nào phù hợp.
                                    </div>
                                ) : (
                                    filteredContacts.map((contact) => (
                                        <button
                                            key={contact.userId}
                                            type="button"
                                            disabled={isOpeningDirect}
                                            onClick={() => void handleStartDirectChat(contact.userId)}
                                            className="w-full px-3 py-2.5 text-left hover:bg-muted/70 transition-colors flex items-center justify-between gap-2 disabled:opacity-50"
                                        >
                                            <div className="min-w-0">
                                                <p className="text-xs font-semibold text-foreground truncate">
                                                    {contact.fullName}
                                                </p>
                                                <p className="text-[11px] text-muted-foreground truncate">
                                                    {contact.email} • {contact.classNames.join(", ")}
                                                </p>
                                            </div>
                                            <Badge variant="outline" className="text-[10px] shrink-0">
                                                Nhắn tin
                                            </Badge>
                                        </button>
                                    ))
                                )}
                            </div>
                        </div>

                        {/* Fallback: Manual User ID */}
                        <form
                            onSubmit={(e) => {
                                e.preventDefault();
                                void handleStartDirectChat(Number(manualUserId));
                            }}
                            className="space-y-2 pt-3 border-t border-border"
                        >
                            <label className="text-xs font-semibold text-slate-700">
                                Hoặc nhập ID người dùng trực tiếp
                            </label>
                            <div className="flex items-center gap-2">
                                <Input
                                    type="number"
                                    min={1}
                                    value={manualUserId}
                                    onChange={(e) => setManualUserId(e.target.value)}
                                    placeholder="Ví dụ: 12"
                                    className="h-9 text-sm"
                                    disabled={isOpeningDirect}
                                />
                                <Button
                                    type="submit"
                                    disabled={isOpeningDirect || !manualUserId.trim()}
                                    className="h-9 shrink-0"
                                >
                                    {isOpeningDirect ? "Đang mở..." : "Mở chat"}
                                </Button>
                            </div>
                        </form>
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setIsNewChatOpen(false)}
                            disabled={isOpeningDirect}
                        >
                            Đóng
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default MessagesPage;
