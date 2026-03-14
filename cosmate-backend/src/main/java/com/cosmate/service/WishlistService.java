package com.cosmate.service;

import com.cosmate.dto.request.CreateWishlistRequest;
import com.cosmate.dto.response.WishlistResponse;

import java.util.List;

public interface WishlistService {
    WishlistResponse create(Integer userId, CreateWishlistRequest request);
    void delete(Integer userId, Integer id);
    WishlistResponse getById(Integer userId, Integer id);
    List<WishlistResponse> listAllByUser(Integer userId);
}

