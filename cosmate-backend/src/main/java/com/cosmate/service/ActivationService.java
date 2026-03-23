package com.cosmate.service;

import com.cosmate.entity.ActivationToken;
import com.cosmate.entity.User;

public interface ActivationService {
    ActivationToken createTokenForUser(User user);
    void activate(String token);
}

