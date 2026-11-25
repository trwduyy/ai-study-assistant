import { useCallback, useEffect, useRef, useState } from "react";
import { AiJobResponse, AiJobStatus } from "../types";
import aiService from "../services/aiService";
import wsClient from "../services/wsClient";

interface UseAiJobOptions {
    onCompleted?: (job: AiJobResponse) => void;
    onError?: (errorMessage: string) => void;
}

export interface UseAiJobReturn {
    jobId: string | null;
    job: AiJobResponse | null;
    status: AiJobStatus | null;
    isLoading: boolean;
    isCompleted: boolean;
    isFailed: boolean;
    isCancelled: boolean;
    error: string | null;
    progressText: string;
    startJob: (id: string) => void;
    cancel: () => Promise<void>;
    reset: () => void;
}

export function useAiJob(initialJobId?: string | null, options?: UseAiJobOptions): UseAiJobReturn {
    const [jobId, setJobId] = useState<string | null>(initialJobId ?? null);
    const [job, setJob] = useState<AiJobResponse | null>(null);
    const [error, setError] = useState<string | null>(null);

    const onCompletedRef = useRef(options?.onCompleted);
    onCompletedRef.current = options?.onCompleted;

    const onErrorRef = useRef(options?.onError);
    onErrorRef.current = options?.onError;

    const isTerminalStatus = (st?: AiJobStatus | null): boolean => {
        return st === "SUCCEEDED" || st === "FAILED" || st === "CANCELLED";
    };

    const fetchJobStatus = useCallback(async (id: string) => {
        try {
            const data = await aiService.getJob(id);
            setJob(data);

            if (data.status === "SUCCEEDED") {
                onCompletedRef.current?.(data);
            } else if (data.status === "FAILED") {
                const errMsg = data.errorMessage || "Tác vụ AI thất bại. Vui lòng thử lại.";
                setError(errMsg);
                onErrorRef.current?.(errMsg);
            }
            return data;
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : "Không thể kiểm tra tiến trình tác vụ.";
            setError(msg);
            return null;
        }
    }, []);

    // 1. Subscribe to WebSocket /user/queue/ai-jobs
    useEffect(() => {
        if (!jobId) {
            return;
        }

        const unsubscribe = wsClient.subscribe("/user/queue/ai-jobs", (message) => {
            try {
                const payload = JSON.parse(message.body) as AiJobResponse;
                if (payload.jobId === jobId) {
                    setJob(payload);
                    if (payload.status === "SUCCEEDED") {
                        onCompletedRef.current?.(payload);
                    } else if (payload.status === "FAILED") {
                        const errMsg = payload.errorMessage || "Tác vụ AI thất bại.";
                        setError(errMsg);
                        onErrorRef.current?.(errMsg);
                    }
                }
            } catch {
                // Ignore parsing errors for malformed socket messages
            }
        });

        return () => {
            unsubscribe();
        };
    }, [jobId]);

    // 2. Fallback polling every 2 seconds until terminal
    useEffect(() => {
        if (!jobId) {
            return;
        }

        let isMounted = true;
        // Initial fetch
        void fetchJobStatus(jobId);

        const interval = setInterval(async () => {
            if (!isMounted) return;
            if (job && isTerminalStatus(job.status)) {
                clearInterval(interval);
                return;
            }
            const updated = await fetchJobStatus(jobId);
            if (updated && isTerminalStatus(updated.status)) {
                clearInterval(interval);
            }
        }, 2000);

        return () => {
            isMounted = false;
            clearInterval(interval);
        };
    }, [jobId, fetchJobStatus, job]);

    const startJob = useCallback((id: string) => {
        setJobId(id);
        setJob(null);
        setError(null);
    }, []);

    const cancel = useCallback(async () => {
        if (!jobId) return;
        try {
            await aiService.cancelJob(jobId);
            setJob((prev) => (prev ? { ...prev, status: "CANCELLED" } : null));
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : "Không thể hủy tác vụ.";
            setError(msg);
        }
    }, [jobId]);

    const reset = useCallback(() => {
        setJobId(null);
        setJob(null);
        setError(null);
    }, []);

    const status = job?.status ?? null;
    const isLoading = !!jobId && (status === "PENDING" || status === "RUNNING" || status === null);
    const isCompleted = status === "SUCCEEDED";
    const isFailed = status === "FAILED";
    const isCancelled = status === "CANCELLED";

    let progressText = "";
    if (status === "PENDING") {
        progressText = "Đang xếp hàng chờ xử lý...";
    } else if (status === "RUNNING") {
        progressText = "AI đang xử lý nội dung...";
    } else if (status === "SUCCEEDED") {
        progressText = "Xử lý thành công!";
    } else if (status === "FAILED") {
        progressText = error || "Tác vụ AI thất bại.";
    } else if (status === "CANCELLED") {
        progressText = "Tác vụ đã được hủy.";
    }

    return {
        jobId,
        job,
        status,
        isLoading,
        isCompleted,
        isFailed,
        isCancelled,
        error,
        progressText,
        startJob,
        cancel,
        reset
    };
}

export default useAiJob;
