import React, { FormEvent, useState } from "react";
import { GraduationCap, LockKeyhole, Mail, UserRound } from "lucide-react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { extractFriendlyErrorMessage } from "../services/apiClient";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { ErrorBanner } from "../components/common/ErrorBanner";

const RegisterPage: React.FC = () => {
    const { register, isAuthenticated } = useAuth();
    const navigate = useNavigate();
    const [fullName, setFullName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);

    if (isAuthenticated) {
        return <Navigate to="/dashboard" replace />;
    }

    const handleSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();
        setError(null);
        if (password !== confirmPassword) {
            setError("Mật khẩu xác nhận chưa khớp.");
            return;
        }
        try {
            setIsSubmitting(true);
            await register({ fullName: fullName.trim(), email: email.trim(), password });
            navigate("/dashboard", { replace: true });
        } catch (requestError: unknown) {
            setError(extractFriendlyErrorMessage(requestError));
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <main className="flex min-h-screen items-center justify-center bg-background px-4 py-8">
            <section className="w-full max-w-md rounded-xl border border-border bg-surface p-7 shadow-elevated">
                <div className="mb-7 text-center"><div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-secondary text-white"><GraduationCap className="h-7 w-7" /></div><h1 className="text-2xl font-bold text-foreground">Tạo tài khoản</h1><p className="mt-1 text-sm text-muted-foreground">Bắt đầu hành trình học tập cùng AI Study Assistant.</p></div>
                <form onSubmit={handleSubmit} className="space-y-4">
                    {error && <ErrorBanner message={error} />}
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Họ và tên</span><div className="relative"><UserRound className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" autoComplete="name" value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="Nguyễn Văn A" required /></div></label>
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Email</span><div className="relative"><Mail className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ban@example.com" required /></div></label>
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Mật khẩu</span><div className="relative"><LockKeyhole className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" type="password" minLength={8} autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Tối thiểu 8 ký tự" required /></div></label>
                    <label className="block"><span className="mb-1.5 block text-sm font-medium text-foreground">Xác nhận mật khẩu</span><div className="relative"><LockKeyhole className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" /><Input className="pl-9" type="password" minLength={8} autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder="Nhập lại mật khẩu" required /></div></label>
                    <Button type="submit" variant="secondary" className="w-full" isLoading={isSubmitting}>Đăng ký tài khoản</Button>
                </form>
                <p className="mt-6 text-center text-sm text-muted-foreground">Đã có tài khoản? <Link className="font-semibold text-primary hover:text-primary-hover" to="/login">Đăng nhập</Link></p>
            </section>
        </main>
    );
};

export default RegisterPage;
