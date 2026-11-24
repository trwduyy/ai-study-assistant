import React from "react";
import { LogOut, User as UserIcon } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";

interface HeaderProps {
    title?: string;
}

export const Header: React.FC<HeaderProps> = ({ title = "Bảng điều khiển" }) => {
    const { user, logout } = useAuth();

    const handleLogout = () => {
        if (window.confirm("Bạn có chắc chắn muốn đăng xuất không?")) {
            logout();
        }
    };

    return (
        <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between sticky top-0 z-10 shadow-sm">
            <div>
                <h2 className="text-lg font-semibold text-slate-800">{title}</h2>
            </div>

            <div className="flex items-center gap-4">
                {/* User Info Badge */}
                <div className="flex items-center gap-3 py-1 px-2.5 rounded-full bg-slate-50 border border-slate-200">
                    <div className="w-8 h-8 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs uppercase">
                        {user?.fullName ? user.fullName.charAt(0) : <UserIcon className="w-4 h-4" />}
                    </div>
                    <div className="text-left hidden sm:block pr-1">
                        <p className="text-xs font-semibold text-slate-800 leading-tight">
                            {user?.fullName || user?.username}
                        </p>
                        <p className="text-[10px] text-slate-500 leading-tight">
                            {user?.email}
                        </p>
                    </div>
                </div>

                {/* Logout Button */}
                <button
                    onClick={handleLogout}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-rose-600 hover:bg-rose-50 transition-colors border border-rose-200"
                    title="Đăng xuất khỏi tài khoản"
                >
                    <LogOut className="w-4 h-4" />
                    <span className="hidden sm:inline">Đăng xuất</span>
                </button>
            </div>
        </header>
    );
};

export default Header;
