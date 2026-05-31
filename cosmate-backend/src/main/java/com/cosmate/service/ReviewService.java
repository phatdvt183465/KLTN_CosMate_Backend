package com.cosmate.service;

import com.cosmate.dto.request.CreateReviewRequest;
import com.cosmate.dto.request.ProviderReplyRequest;
import com.cosmate.dto.response.ReviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {
    ReviewResponse createReview(Integer cosplayerId, CreateReviewRequest request, List<MultipartFile> files);

    List<ReviewResponse> getByOrderId(Integer orderId);

    List<ReviewResponse> getByProviderId(Integer providerId);
    
    List<ReviewResponse> getByCostumeId(Integer costumeId);

    // Provider replies to a review
    ReviewResponse replyToReview(Integer reviewId, Integer providerId, ProviderReplyRequest request);

    ReviewResponse toggleToxicStatus(Integer reviewId);

    List<ReviewResponse> getAllReviews();
}
