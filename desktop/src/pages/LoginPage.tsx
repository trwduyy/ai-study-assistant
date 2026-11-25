import React, { FormEvent, useState } from "react";
import { GraduationCap, LockKeyhole, Mail } from "lucide-react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { extractFriendlyErrorMessage } from "../services/apiClient";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { ErrorBanner } from "../components/common/ErrorBanner";

interface LocationState {
    from?: { pathname?: string };
}

const LoginPage: React.FC = () => {
    const { login, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    if (isAuthenticated) {
        return <Navigate to="/dashboard" replace />;
    }

    const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();
        setError(null);
        try {
            setIsSubmitting(true);
            await login({ email: email.trim(), password });
            const state = location.state as LocationState | null;
            navigate(state?.from?.pathname ?? "/dashboard", { replace: true });
        } catch (requestError: unknown) {
            setError(extractFriendlyErrorMessage(requestError));
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <main className="flex min-h-screen items-center justify-center bg-background px-4 py-8">
            <section className="w-full max-w-md rounded-xl border border-border bg-surface p-7 shadow-elevated">
                <div className="mb-7 text-center"><div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-primary text-white"><GraduationCap className="h-7 w-7" /></div><h1 className="text-2xl font-bold text-foreground">Chào mừng trở lại</h1><p className="mt-1 text-sm text-muted-foreground">Đăng nhập để tiếp tục học tập và giảng dạy.</p></div>
                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && <ErrorBanner message={error} />}
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Email</span><div className="relative"><Mail className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ban@example.com" required /></div></label>
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Mật khẩu</span><div className="relative"><LockKeyhole className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Nhập mật khẩu" required /></div></label>
                    <Button type="submit" className="w-full" isLoading={isSubmitting}>Đăng nhập</Button>
                </form>
                <p className="mt-6 text-center text-sm text-muted-foreground">Chưa có tài khoản? <Link className="font-semibold text-primary hover:text-primary-hover" to="/register">Đăng ký ngay</Link></p>
            </section>
        </main>
    );
};

export default LoginPage;
