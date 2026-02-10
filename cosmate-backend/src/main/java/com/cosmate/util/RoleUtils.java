package com.cosmate.util;

import com.cosmate.entity.Role;

public class RoleUtils {
    public static boolean isProviderRole(Role r) {
        if (r == null) return false;
        return switch (r) {
            case PROVIDER, PROVIDER_RENTAL, PROVIDER_PHOTOGRAPH, PROVIDER_EVENT_STAFF -> true;
            default -> false;
        };
    }
}
