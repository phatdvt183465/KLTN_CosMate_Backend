package com.cosmate.service;

import com.cosmate.entity.Token;
import com.cosmate.entity.User;

public interface PasswordResetService {
    /**
     * Create a password reset token for a user identified by email or username.
     * @param identifier email or username
     */
    Token createTokenForIdentifier(String identifier);

    void resetPassword(String token, String newPassword);
}


