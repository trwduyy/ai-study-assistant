package com.aistudy.assistant.identity.security;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility methods for extracting the authenticated user's identity and role from {@link SecurityContextHolder}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }

    public static Long currentUserId() {
        return getCurrentUserPrincipal().getId();
    }

    /**
     * Compatibility alias for legacy Phase 1 services until migrated in Phase 1B/1C.
     */
    public static Long getCurrentUserId() {
        return currentUserId();
    }

    public static UserRole currentUserRole() {
        return getCurrentUserPrincipal().getRole();
    }

    public static String currentUserEmail() {
        return getCurrentUserPrincipal().getEmail();
    }
}
