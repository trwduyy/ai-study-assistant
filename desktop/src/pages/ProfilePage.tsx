import React, { FormEvent, useState } from "react";
import { KeyRound, Mail, Save, ShieldCheck, UserRound } from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "../contexts/AuthContext";
import authService from "../services/authService";
import { extractFriendlyErrorMessage } from "../services/apiClient";
import { Avatar, AvatarFallback, AvatarImage } from "../components/ui/avatar";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { ErrorBanner } from "../components/common/ErrorBanner";

const ProfilePage: React.FC = () => {
    const { user, updateUser } = useAuth();
    const [fullName, setFullName] = useState(user?.fullName ?? "");
    const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl ?? "");
    const [currentPassword, setCurrentPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");
    const [profileError, setProfileError] = useState<string | null>(null);
    const [passwordError, setPasswordError] = useState<string | null>(null);
    const [savingProfile, setSavingProfile] = useState(false);
    const [changingPassword, setChangingPassword] = useState(false);

    if (!user) {
        return null;
    }

    const initials = user.fullName.split(" ").filter(Boolean).slice(-2).map((part) => part[0]).join("").toUpperCase();
    const onProfileSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();
        setProfileError(null);
        try {
            setSavingProfile(true);
            const updated = await authService.updateProfile({
                fullName: fullName.trim(),
                avatarUrl: avatarUrl.trim() || null,
                version: user.version
            });
            updateUser(updated);
            toast.success("Đã cập nhật hồ sơ cá nhân.");
        } catch (requestError: unknown) {
            setProfileError(extractFriendlyErrorMessage(requestError));
        } finally {
            setSavingProfile(false);
        }
    };

    const onPasswordSubmit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
        event.preventDefault();
        setPasswordError(null);
        if (newPassword !== confirmPassword) {
            setPasswordError("Mật khẩu mới và mật khẩu xác nhận chưa khớp.");
            return;
        }
        try {
            setChangingPassword(true);
            await authService.changePassword({ currentPassword, newPassword });
            setCurrentPassword("");
            setNewPassword("");
            setConfirmPassword("");
            toast.success("Đổi mật khẩu thành công.");
        } catch (requestError: unknown) {
            setPasswordError(extractFriendlyErrorMessage(requestError));
        } finally {
            setChangingPassword(false);
        }
    };

    return (
        <div className="mx-auto max-w-4xl space-y-6">
            <div><h1 className="text-2xl font-bold text-foreground">Hồ sơ cá nhân</h1><p className="mt-1 text-muted-foreground">Quản lý thông tin tài khoản và bảo mật đăng nhập.</p></div>
            <Card><CardContent className="flex flex-col items-center gap-4 p-6 sm:flex-row sm:items-start"><Avatar className="h-16 w-16"><AvatarImage src={user.avatarUrl ?? undefined} alt="Ảnh đại diện" /><AvatarFallback className="text-lg">{initials}</AvatarFallback></Avatar><div className="flex-1 text-center sm:text-left"><h2 className="text-lg font-semibold text-foreground">{user.fullName}</h2><p className="mt-1 flex items-center justify-center gap-2 text-sm text-muted-foreground sm:justify-start"><Mail className="h-4 w-4" />{user.email}</p><div className="mt-3"><Badge variant="info" icon={<ShieldCheck className="h-3.5 w-3.5" />}>{user.role === "STUDENT" ? "Sinh viên" : user.role === "TEACHER" ? "Giảng viên" : "Quản trị viên"}</Badge></div></div></CardContent></Card>
            <div className="grid gap-6 lg:grid-cols-2">
                <Card><CardHeader><CardTitle className="flex items-center gap-2"><UserRound className="h-5 w-5 text-primary" />Thông tin cá nhân</CardTitle></CardHeader><CardContent><form onSubmit={onProfileSubmit} className="space-y-4">{profileError && <ErrorBanner message={profileError} />}<label className="block"><span className="mb-1.5 block text-sm font-medium">Họ và tên</span><Input value={fullName} onChange={(event) => setFullName(event.target.value)} required /></label><label className="block"><span className="mb-1.5 block text-sm font-medium">Email</span><Input value={user.email} disabled /></label><label className="block"><span className="mb-1.5 block text-sm font-medium">Đường dẫn ảnh đại diện</span><Input type="url" value={avatarUrl} onChange={(event) => setAvatarUrl(event.target.value)} placeholder="https://..." /></label><Button type="submit" isLoading={savingProfile}><Save className="h-4 w-4" />Lưu thay đổi</Button></form></CardContent></Card>
                <Card><CardHeader><CardTitle className="flex items-center gap-2"><KeyRound className="h-5 w-5 text-primary" />Đổi mật khẩu</CardTitle></CardHeader><CardContent><form onSubmit={onPasswordSubmit} className="space-y-4">{passwordError && <ErrorBanner message={passwordError} />}<label className="block"><span className="mb-1.5 block text-sm font-medium">Mật khẩu hiện tại</span><Input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required /></label><label className="block"><span className="mb-1.5 block text-sm font-medium">Mật khẩu mới</span><Input type="password" minLength={8} autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required /></label><label className="block"><span className="mb-1.5 block text-sm font-medium">Xác nhận mật khẩu mới</span><Input type="password" minLength={8} autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required /></label><Button type="submit" variant="secondary" isLoading={changingPassword}><KeyRound className="h-4 w-4" />Cập nhật mật khẩu</Button></form></CardContent></Card>
            </div>
        </div>
    );
};

export default ProfilePage;
