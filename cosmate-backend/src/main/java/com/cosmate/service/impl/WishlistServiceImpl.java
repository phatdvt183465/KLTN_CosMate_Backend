package com.cosmate.service.impl;

import com.cosmate.dto.request.CreateWishlistRequest;
import com.cosmate.dto.response.WishlistResponse;
import com.cosmate.entity.Costume;
import com.cosmate.entity.User;
import com.cosmate.entity.WishlistCostume;
import com.cosmate.exception.AppException;
import com.cosmate.exception.ErrorCode;
import com.cosmate.repository.CostumeRepository;
import com.cosmate.repository.UserRepository;
import com.cosmate.repository.WishlistRepository;
import com.cosmate.service.CostumeService;
import com.cosmate.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final CostumeRepository costumeRepository;
    private final CostumeService costumeService;

    private WishlistResponse toResponse(WishlistCostume w) {
        com.cosmate.dto.response.CostumeResponse costumeResp = null;
        try {
            costumeResp = costumeService.getById(w.getCostumeId());
        } catch (Exception e) {
            // ignore if costume mapping fails; return null costume
        }
        return WishlistResponse.builder()
                .id(w.getId())
                .userId(w.getUserId())
                .costumeId(w.getCostumeId())
                .createdAt(w.getCreatedAt())
                .costume(costumeResp)
                .build();
    }

    @Override
    public WishlistResponse create(Integer userId, CreateWishlistRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        costumeRepository.findById(request.getCostumeId()).orElseThrow(() -> new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION));

        // prevent duplicate
        var existing = wishlistRepository.findByUserIdAndCostumeId(userId, request.getCostumeId());
        if (existing.isPresent()) return toResponse(existing.get());

        WishlistCostume w = WishlistCostume.builder()
                .userId(userId)
                .costumeId(request.getCostumeId())
                .build();
        w = wishlistRepository.save(w);
        return toResponse(w);
    }

    @Override
    public void delete(Integer userId, Integer id) {
        WishlistCostume w = wishlistRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.WISHLIST_NOT_FOUND));
        if (!w.getUserId().equals(userId)) throw new AppException(ErrorCode.FORBIDDEN);
        wishlistRepository.deleteById(id);
    }

    @Override
    public WishlistResponse getById(Integer userId, Integer id) {
        WishlistCostume w = wishlistRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.WISHLIST_NOT_FOUND));
        if (!w.getUserId().equals(userId)) throw new AppException(ErrorCode.FORBIDDEN);
        return toResponse(w);
    }

    @Override
    public List<WishlistResponse> listAllByUser(Integer userId) {
        userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return wishlistRepository.findAllByUserId(userId).stream().map(this::toResponse).collect(Collectors.toList());
    }
}

