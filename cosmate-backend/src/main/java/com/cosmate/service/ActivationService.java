package com.cosmate.service;

import com.cosmate.entity.Token;
import com.cosmate.entity.User;

public interface ActivationService {
    Token createTokenForUser(User user);
    void activate(String token);
}

