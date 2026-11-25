import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { IMessage } from "@stomp/stompjs";
import { AiJobResponse, NotificationItem, WsConnectionStatus } from "../types";
import wsClient, { StompMessageCallback } from "../services/wsClient";
import { useAuth } from "./AuthContext";

interface WebSocketContextValue {
    status: WsConnectionStatus;
    subscribeNotifications: (callback: (notification: NotificationItem) => void) => () => void;
    subscribeAiJobs: (callback: (job: AiJobResponse) => void) => () => void;
    subscribeConversation: (conversationId: number, callback: StompMessageCallback) => () => void;
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

    const subscribeNotifications = useCallback((callback: (notification: NotificationItem) => void) => wsClient.subscribe("/user/queue/notifications", (message) => parseMessage(message, callback)), []);
    const subscribeAiJobs = useCallback((callback: (job: AiJobResponse) => void) => wsClient.subscribe("/user/queue/ai-jobs", (message) => parseMessage(message, callback)), []);
    const subscribeConversation = useCallback((conversationId: number, callback: StompMessageCallback) => wsClient.subscribe(`/topic/conversations/${conversationId}`, callback), []);
    const value = useMemo<WebSocketContextValue>(() => ({ status, subscribeNotifications, subscribeAiJobs, subscribeConversation }), [status, subscribeAiJobs, subscribeConversation, subscribeNotifications]);
    return <WebSocketContext.Provider value={value}>{children}</WebSocketContext.Provider>;
};

export function useWebSocket(): WebSocketContextValue {
    const context = useContext(WebSocketContext);
    if (!context) {
        throw new Error("useWebSocket phải được sử dụng bên trong WebSocketProvider.");
    }
    return context;
}
