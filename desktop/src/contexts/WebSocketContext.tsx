import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { IMessage } from "@stomp/stompjs";
import {
    AiJobResponse,
    ChatMessage,
    NotificationItem,
    SendMessageRequest,
    WsConnectionStatus
} from "../types";
import wsClient from "../services/wsClient";
import { useAuth } from "./AuthContext";

interface WebSocketContextValue {
    status: WsConnectionStatus;
    subscribeNotifications: (callback: (notification: NotificationItem) => void) => () => void;
    subscribeAiJobs: (callback: (job: AiJobResponse) => void) => () => void;
    subscribeConversation: (
        conversationId: number,
        callback: (message: ChatMessage) => void
    ) => () => void;
    sendConversationMessage: (conversationId: number, payload: SendMessageRequest) => boolean;
}

const WebSocketContext = createContext<WebSocketContextValue | undefined>(undefined);

function parseMessage<T>(message: IMessage, callback: (value: T) => void): void {
    try {
        callback(JSON.parse(message.body) as T);
    } catch {
        // Ignore malformed events; REST remains the authoritative source.
    }
}

export const WebSocketProvider: React.FC<React.PropsWithChildren> = ({ children }) => {
    const { isAuthenticated } = useAuth();
    const [status, setStatus] = useState<WsConnectionStatus>(wsClient.getStatus());

    useEffect(() => wsClient.subscribeStatus(setStatus), []);
    useEffect(() => {
        if (isAuthenticated) {
            wsClient.connect();
        } else {
            wsClient.disconnect();
        }
        return () => wsClient.disconnect();
    }, [isAuthenticated]);

    const subscribeNotifications = useCallback(
        (callback: (notification: NotificationItem) => void) =>
            wsClient.subscribe("/user/queue/notifications", (message) =>
                parseMessage(message, callback)
            ),
        []
    );

    const subscribeAiJobs = useCallback(
        (callback: (job: AiJobResponse) => void) =>
            wsClient.subscribe("/user/queue/ai-jobs", (message) =>
                parseMessage(message, callback)
            ),
        []
    );

    const subscribeConversation = useCallback(
        (conversationId: number, callback: (chatMessage: ChatMessage) => void) =>
            wsClient.subscribe(`/topic/conversations/${conversationId}`, (message) =>
                parseMessage(message, callback)
            ),
        []
    );

    const sendConversationMessage = useCallback(
        (conversationId: number, payload: SendMessageRequest): boolean =>
            wsClient.send(`/app/conversations/${conversationId}/messages`, payload),
        []
    );

    const value = useMemo<WebSocketContextValue>(
        () => ({
            status,
            subscribeNotifications,
            subscribeAiJobs,
            subscribeConversation,
            sendConversationMessage
        }),
        [
            status,
            subscribeAiJobs,
            subscribeConversation,
            subscribeNotifications,
            sendConversationMessage
        ]
    );

    return <WebSocketContext.Provider value={value}>{children}</WebSocketContext.Provider>;
};

export function useWebSocket(): WebSocketContextValue {
    const context = useContext(WebSocketContext);
    if (!context) {
        throw new Error("useWebSocket phải được sử dụng bên trong WebSocketProvider.");
    }
    return context;
}
