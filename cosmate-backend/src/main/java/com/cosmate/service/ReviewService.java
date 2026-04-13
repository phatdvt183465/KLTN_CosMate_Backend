package com.cosmate.service;

import com.cosmate.dto.request.CreateReviewRequest;
import com.cosmate.dto.response.ReviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {
    ReviewResponse createReview(Integer cosplayerId, CreateReviewRequest request, List<MultipartFile> files);

    List<ReviewResponse> getByOrderId(Integer orderId);

    List<ReviewResponse> getByProviderId(Integer providerId);
    
    List<ReviewResponse> getByCostumeId(Integer costumeId);
}
