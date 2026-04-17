package com.cosmate.service;

import com.cosmate.dto.request.RegisterRequest;
import com.cosmate.entity.User;
import com.cosmate.dto.request.ChangePasswordRequest;
import com.cosmate.dto.request.UpdateProfileRequest;
import com.cosmate.dto.request.GoogleTokenRequest;
import com.cosmate.dto.response.UserListItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    // avatarUrl is optional and passed separately; avatar file upload handled at controller/service boundary
    User register(RegisterRequest request, boolean fromGoogle, String avatarUrl);
    String authenticate(String usernameOrEmail, String password);

    // accept avatar file so service can upload and persist avatarUrl
    User updateProfile(Integer userId, UpdateProfileRequest request, MultipartFile avatar);

    // New: update only avatar
    User updateAvatar(Integer userId, MultipartFile avatar);

    void changePassword(Integer userId, ChangePasswordRequest request);

    void lockUser(Integer targetUserId);
    void unlockUser(Integer targetUserId);
    void banUser(Integer targetUserId);
    void unbanUser(Integer targetUserId);

    List<UserListItem> listUsers();
    List<UserListItem> searchUsers(String keyword);

    // New methods for Google token flows
    String loginWithGoogleToken(GoogleTokenRequest request);
    String registerWithGoogleToken(GoogleTokenRequest request);

    // Retrieve user by id (for profile view)
    User getById(Integer userId);
}
