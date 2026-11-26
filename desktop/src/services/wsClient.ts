import { Client, IFrame, IMessage, StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { WsConnectionStatus } from "../types";
import tokenStorage from "./tokenStorage";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api/v1";

function resolveWsEndpoint(): string {
    const explicitWs = import.meta.env.VITE_WS_URL;
    if (explicitWs) {
        return explicitWs;
    }
    const baseWithoutApi = API_BASE_URL.replace(/\/api\/v\d+\/?$/, "").replace(/\/+$/, "");
    return `${baseWithoutApi}/ws`;
}

export type StompMessageCallback = (message: IMessage) => void;
type StatusListener = (status: WsConnectionStatus) => void;

interface SubscriptionEntry {
    destination: string;
    callback: StompMessageCallback;
    stompSub: StompSubscription | null;
}

class WsClientManager {
    private client: Client | null = null;
    private status: WsConnectionStatus = "DISCONNECTED";
    private statusListeners = new Set<StatusListener>();
    private subscriptions = new Map<string, SubscriptionEntry>();
    private nextSubKey = 1;
    private isManualDisconnect = false;

    public getStatus(): WsConnectionStatus {
        return this.status;
    }

    public subscribeStatus(listener: StatusListener): () => void {
        this.statusListeners.add(listener);
        listener(this.status);
        return () => {
            this.statusListeners.delete(listener);
        };
    }

    private setStatus(nextStatus: WsConnectionStatus): void {
        if (this.status === nextStatus) {
            return;
        }
        this.status = nextStatus;
        this.statusListeners.forEach((listener) => {
            try {
                listener(nextStatus);
            } catch {
                // Keep notifying remaining listeners
            }
        });
    }

    public connect(): void {
        const token = tokenStorage.getAccessToken();
        if (!token) {
            this.disconnect();
            return;
        }

        if (this.client?.active) {
            return;
        }

        this.isManualDisconnect = false;
        this.setStatus("CONNECTING");

        const wsEndpoint = resolveWsEndpoint();

        const client = new Client({
            webSocketFactory: () => new SockJS(wsEndpoint),
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            beforeConnect: () => {
                const latestToken = tokenStorage.getAccessToken();
                if (!latestToken) {
                    void client.deactivate();
                    return;
                }
                client.connectHeaders = {
                    Authorization: `Bearer ${latestToken}`
                };
                if (this.status !== "CONNECTING") {
                    this.setStatus("RECONNECTING");
                }
            },
            onConnect: () => {
                this.setStatus("CONNECTED");
                this.resubscribeAll();
            },
            onDisconnect: () => {
                if (this.isManualDisconnect) {
                    this.setStatus("DISCONNECTED");
                } else {
                    this.setStatus("RECONNECTING");
                }
            },
            onWebSocketClose: () => {
                this.clearActiveStompSubs();
                if (this.isManualDisconnect) {
                    this.setStatus("DISCONNECTED");
                } else if (tokenStorage.getAccessToken()) {
                    this.setStatus("RECONNECTING");
                } else {
                    this.setStatus("DISCONNECTED");
                }
            },
            onStompError: (_frame: IFrame) => {
                this.setStatus("ERROR");
            },
            onWebSocketError: () => {
                if (!this.isManualDisconnect) {
                    this.setStatus("RECONNECTING");
                }
            }
        });

        this.client = client;
        client.activate();
    }

    public disconnect(): void {
        this.isManualDisconnect = true;
        this.clearActiveStompSubs();
        if (this.client) {
            const activeClient = this.client;
            this.client = null;
            void activeClient.deactivate();
        }
        this.setStatus("DISCONNECTED");
    }

    public subscribe(destination: string, callback: StompMessageCallback): () => void {
        const key = `sub-${this.nextSubKey++}`;
        const entry: SubscriptionEntry = {
            destination,
            callback,
            stompSub: null
        };

        if (this.client?.connected) {
            entry.stompSub = this.client.subscribe(destination, callback);
        }

        this.subscriptions.set(key, entry);

        return () => {
            const existing = this.subscriptions.get(key);
            if (existing?.stompSub) {
                try {
                    existing.stompSub.unsubscribe();
                } catch {
                    // Ignore unsubscribe error if socket already closed
                }
            }
            this.subscriptions.delete(key);
        };
    }

    public send(destination: string, body: unknown): boolean {
        if (!this.client?.connected) {
            return false;
        }
        this.client.publish({
            destination,
            body: typeof body === "string" ? body : JSON.stringify(body)
        });
        return true;
    }

    private resubscribeAll(): void {
        if (!this.client?.connected) {
            return;
        }
        this.subscriptions.forEach((entry) => {
            if (entry.stompSub) {
                try {
                    entry.stompSub.unsubscribe();
                } catch {
                    // Ignore stale subscription cleanup errors
                }
            }
            entry.stompSub = this.client?.subscribe(entry.destination, entry.callback) ?? null;
        });
    }

    private clearActiveStompSubs(): void {
        this.subscriptions.forEach((entry) => {
            entry.stompSub = null;
        });
    }
}

export const wsClient = new WsClientManager();

export default wsClient;
