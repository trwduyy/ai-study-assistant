import { User } from "../types";

const ACCESS_TOKEN_KEY = "asa_access_token";
const REFRESH_TOKEN_KEY = "asa_refresh_token";
const USER_KEY = "asa_user";

// Legacy keys to clean up when migrating or clearing session
const LEGACY_TOKEN_KEY = "token";
const LEGACY_USER_KEY = "user";

type SessionListener = () => void;

const listeners = new Set<SessionListener>();

function notifyListeners(): void {
    listeners.forEach((listener) => {
        try {
            listener();
        } catch {
            // Ignore listener errors so other subscribers remain updated
        }
    });
}

export const tokenStorage = {
    getAccessToken(): string | null {
        return localStorage.getItem(ACCESS_TOKEN_KEY) || localStorage.getItem(LEGACY_TOKEN_KEY);
    },

    getRefreshToken(): string | null {
        return localStorage.getItem(REFRESH_TOKEN_KEY);
    },

    getUser(): User | null {
        const raw = localStorage.getItem(USER_KEY);
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw) as User;
        } catch {
            localStorage.removeItem(USER_KEY);
            return null;
        }
    },

    setTokens(accessToken: string, refreshToken: string): void {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        localStorage.removeItem(LEGACY_TOKEN_KEY);
        notifyListeners();
    },

    setUser(user: User): void {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        localStorage.removeItem(LEGACY_USER_KEY);
        notifyListeners();
    },

    setSession(accessToken: string, refreshToken: string, user: User): void {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        localStorage.removeItem(LEGACY_TOKEN_KEY);
        localStorage.removeItem(LEGACY_USER_KEY);
        notifyListeners();
    },

    clear(): void {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
        localStorage.removeItem(LEGACY_TOKEN_KEY);
        localStorage.removeItem(LEGACY_USER_KEY);
        notifyListeners();
    },

    subscribe(listener: SessionListener): () => void {
        listeners.add(listener);
        return () => {
            listeners.delete(listener);
        };
    }
};

export default tokenStorage;
