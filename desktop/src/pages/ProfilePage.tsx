import React, { useState } from "react";
import { useAuth } from "../contexts/AuthContext";
import authService from "../services/authService";
import {
    User,
    Lock,
    Mail,
    Calendar,
    ShieldCheck,
    CheckCircle,
    AlertCircle,
    Save,
    KeyRound
} from "lucide-react";

export const ProfilePage: React.FC = () => {
    const { user, updateUser } = useAuth();

    // Profile form state
    const [fullName, setFullName] = useState<string>(user?.fullName || "");
    const [isUpdatingProfile, setIsUpdatingProfile] = useState<boolean>(false);
    const [profileSuccessMsg, setProfileSuccessMsg] = useState<string | null>(null);
    const [profileErrorMsg, setProfileErrorMsg] = useState<string | null>(null);

    // Password form state
    const [oldPassword, setOldPassword] = useState<string>("");
    const [newPassword, setNewPassword] = useState<string>("");
    const [confirmPassword, setConfirmPassword] = useState<string>("");
    const [isChangingPassword, setIsChangingPassword] = useState<boolean>(false);
    const [passwordSuccessMsg, setPasswordSuccessMsg] = useState<string | null>(null);
    const [passwordErrorMsg, setPasswordErrorMsg] = useState<string | null>(null);

    const handleUpdateProfile = async (e: React.FormEvent) => {
        e.preventDefault();
        setProfileSuccessMsg(null);
        setProfileErrorMsg(null);

        if (!fullName.trim()) {
            setProfileErrorMsg("Họ và tên không được để trống.");
            return;
        }

        try {
            setIsUpdatingProfile(true);
            const updatedUser = await authService.updateProfile({ fullName: fullName.trim() });
            updateUser(updatedUser);
            setProfileSuccessMsg("Cập nhật thông tin cá nhân thành công!");
        } catch (err: unknown) {
            const error = err as { response?: { data?: { message?: string } } };
            setProfileErrorMsg(error.response?.data?.message || "Không thể cập nhật thông tin. Vui lòng thử lại.");
        } finally {
            setIsUpdatingProfile(false);
        }
    };

    const handleChangePassword = async (e: React.FormEvent) => {
        e.preventDefault();
        setPasswordSuccessMsg(null);
        setPasswordErrorMsg(null);

        if (!oldPassword) {
            setPasswordErrorMsg("Vui lòng nhập mật khẩu hiện tại.");
            return;
        }
        if (newPassword.length < 6) {
            setPasswordErrorMsg("Mật khẩu mới phải có ít nhất 6 ký tự.");
            return;
        }
        if (newPassword !== confirmPassword) {
            setPasswordErrorMsg("Mật khẩu mới và xác nhận mật khẩu không khớp.");
            return;
        }

        try {
            setIsChangingPassword(true);
            await authService.changePassword({ oldPassword, newPassword });
            setPasswordSuccessMsg("Đổi mật khẩu thành công!");
            setOldPassword("");
            setNewPassword("");
            setConfirmPassword("");
        } catch (err: unknown) {
            const error = err as { response?: { data?: { message?: string } } };
            setPasswordErrorMsg(error.response?.data?.message || "Đổi mật khẩu thất bại. Mật khẩu hiện tại có thể không đúng.");
        } finally {
            setIsChangingPassword(false);
        }
    };

    return (
        <div className="space-y-8 max-w-4xl mx-auto">
            <div>
                <h1 className="text-2xl font-bold text-slate-800">Hồ sơ cá nhân</h1>
                <p className="text-slate-500 mt-1">Quản lý thông tin tài khoản và thiết lập bảo mật sinh viên</p>
            </div>

            {/* Profile Overview Card */}
            <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 flex flex-col md:flex-row items-center gap-6">
                <div className="w-20 h-20 rounded-full bg-indigo-600 text-white flex items-center justify-center text-3xl font-bold shadow-md shadow-indigo-100 flex-shrink-0">
                    {user?.fullName?.charAt(0)?.toUpperCase() || user?.username?.charAt(0)?.toUpperCase() || "U"}
                </div>
                <div className="flex-1 text-center md:text-left space-y-1">
                    <h2 className="text-xl font-bold text-slate-800">{user?.fullName || "Chưa đặt tên"}</h2>
                    <p className="text-sm text-slate-500 flex items-center justify-center md:justify-start gap-1.5">
                        <User className="w-4 h-4 text-slate-400" />
                        <span>Tên đăng nhập: <strong className="text-slate-700">@{user?.username}</strong></span>
                    </p>
                    <p className="text-sm text-slate-500 flex items-center justify-center md:justify-start gap-1.5">
                        <Mail className="w-4 h-4 text-slate-400" />
                        <span>Email: <span className="text-slate-700">{user?.email}</span></span>
                    </p>
                </div>
                <div className="bg-slate-50 border border-slate-200 rounded-lg p-3 text-xs text-slate-600 space-y-1 w-full md:w-auto">
                    <div className="flex items-center gap-1.5 text-emerald-600 font-medium">
                        <ShieldCheck className="w-4 h-4" />
                        <span>Tài khoản đã kích hoạt</span>
                    </div>
                    {user?.createdAt && (
                        <div className="flex items-center gap-1.5 text-slate-500">
                            <Calendar className="w-3.5 h-3.5" />
                            <span>Tham gia: {new Date(user.createdAt).toLocaleDateString("vi-VN")}</span>
                        </div>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Edit Profile Form */}
                <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-5">
                    <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                        <User className="w-5 h-5 text-indigo-600" />
                        <h3 className="font-semibold text-slate-800">Cập nhật thông tin</h3>
                    </div>

                    {profileSuccessMsg && (
                        <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-sm text-emerald-700 flex items-center gap-2">
                            <CheckCircle className="w-4 h-4 flex-shrink-0" />
                            <span>{profileSuccessMsg}</span>
                        </div>
                    )}
                    {profileErrorMsg && (
                        <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-sm text-rose-700 flex items-center gap-2">
                            <AlertCircle className="w-4 h-4 flex-shrink-0" />
                            <span>{profileErrorMsg}</span>
                        </div>
                    )}

                    <form onSubmit={handleUpdateProfile} className="space-y-4">
                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Họ và tên hiển thị
                            </label>
                            <input
                                type="text"
                                value={fullName}
                                onChange={(e) => setFullName(e.target.value)}
                                className="w-full px-3.5 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
                                placeholder="Nhập họ và tên sinh viên"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Tên đăng nhập (Username)
                            </label>
                            <input
                                type="text"
                                value={user?.username || ""}
                                disabled
                                className="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-500 cursor-not-allowed"
                            />
                            <p className="text-xs text-slate-400 mt-1">Tên đăng nhập không thể thay đổi</p>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Địa chỉ Email
                            </label>
                            <input
                                type="email"
                                value={user?.email || ""}
                                disabled
                                className="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-lg text-sm text-slate-500 cursor-not-allowed"
                            />
                            <p className="text-xs text-slate-400 mt-1">Email được liên kết với tài khoản hệ thống</p>
                        </div>

                        <div className="pt-2">
                            <button
                                type="submit"
                                disabled={isUpdatingProfile}
                                className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-300 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
                            >
                                <Save className="w-4 h-4" />
                                {isUpdatingProfile ? "Đang lưu..." : "Lưu thay đổi"}
                            </button>
                        </div>
                    </form>
                </div>

                {/* Change Password Form */}
                <div className="bg-white rounded-xl shadow-sm border border-slate-200 p-6 space-y-5">
                    <div className="flex items-center gap-2 border-b border-slate-100 pb-3">
                        <KeyRound className="w-5 h-5 text-indigo-600" />
                        <h3 className="font-semibold text-slate-800">Đổi mật khẩu</h3>
                    </div>

                    {passwordSuccessMsg && (
                        <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-lg text-sm text-emerald-700 flex items-center gap-2">
                            <CheckCircle className="w-4 h-4 flex-shrink-0" />
                            <span>{passwordSuccessMsg}</span>
                        </div>
                    )}
                    {passwordErrorMsg && (
                        <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-sm text-rose-700 flex items-center gap-2">
                            <AlertCircle className="w-4 h-4 flex-shrink-0" />
                            <span>{passwordErrorMsg}</span>
                        </div>
                    )}

                    <form onSubmit={handleChangePassword} className="space-y-4">
                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Mật khẩu hiện tại
                            </label>
                            <div className="relative">
                                <input
                                    type="password"
                                    value={oldPassword}
                                    onChange={(e) => setOldPassword(e.target.value)}
                                    className="w-full px-3.5 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all pr-10"
                                    placeholder="••••••••"
                                    required
                                />
                                <Lock className="w-4 h-4 text-slate-400 absolute right-3 top-3" />
                            </div>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Mật khẩu mới
                            </label>
                            <div className="relative">
                                <input
                                    type="password"
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    className="w-full px-3.5 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all pr-10"
                                    placeholder="Tối thiểu 6 ký tự"
                                    required
                                />
                                <Lock className="w-4 h-4 text-slate-400 absolute right-3 top-3" />
                            </div>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold uppercase text-slate-500 mb-1">
                                Xác nhận mật khẩu mới
                            </label>
                            <div className="relative">
                                <input
                                    type="password"
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className="w-full px-3.5 py-2.5 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all pr-10"
                                    placeholder="Nhập lại mật khẩu mới"
                                    required
                                />
                                <Lock className="w-4 h-4 text-slate-400 absolute right-3 top-3" />
                            </div>
                        </div>

                        <div className="pt-2">
                            <button
                                type="submit"
                                disabled={isChangingPassword}
                                className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-slate-800 hover:bg-slate-900 disabled:bg-slate-400 text-white text-sm font-medium rounded-lg transition-colors shadow-sm"
                            >
                                <KeyRound className="w-4 h-4" />
                                {isChangingPassword ? "Đang xử lý..." : "Cập nhật mật khẩu"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;
