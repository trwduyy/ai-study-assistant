import React from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { UserRole } from "../../types";
import { LoadingState } from "./LoadingState";

interface ProtectedRouteProps {
    children?: React.ReactNode;
    allowedRoles?: UserRole[];
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, allowedRoles }) => {
    const { isAuthenticated, isLoading, user } = useAuth();
    const location = useLocation();

    if (isLoading) {
        return (
            <div className="min-h-screen flex items-center justify-center bg-background"><LoadingState label="Đang xác thực phiên làm việc..." /></div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    if (allowedRoles && (!user || !allowedRoles.includes(user.role))) {
        return <Navigate to="/dashboard" replace />;
    }

    return children ? <>{children}</> : <Outlet />;
};

export default ProtectedRoute;
