package com.cosmate.service;

import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.entity.User;
import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.request.GoogleTokenRequest;
import com.cosmate.dto.response.UserListItem;

import java.util.List;

public interface UserService {
    User register(RegisterRequest request, boolean fromGoogle);
    String authenticate(String usernameOrEmail, String password);

    User updateProfile(Integer userId, UpdateProfileRequest request);
    void changePassword(Integer userId, ChangePasswordRequest request);

    void lockUser(Integer targetUserId);
    void unlockUser(Integer targetUserId);
    void banUser(Integer targetUserId);
    void unbanUser(Integer targetUserId);

    List<UserListItem> listUsers();

    // New methods for Google token flows
    String loginWithGoogleToken(GoogleTokenRequest request);
    String registerWithGoogleToken(GoogleTokenRequest request);

    // Retrieve user by id (for profile view)
    User getById(Integer userId);
}
