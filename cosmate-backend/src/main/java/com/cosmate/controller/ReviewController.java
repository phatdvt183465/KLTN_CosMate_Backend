package com.cosmate.controller;

import com.cosmate.dto.request.CreateReviewRequest;
import com.cosmate.dto.response.ApiResponse;
import com.cosmate.dto.response.ReviewResponse;
import com.cosmate.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @RequestParam Integer cosplayerId,
            @RequestParam Integer orderId,
            @RequestParam Integer rating,
            @RequestParam(required = false) String comment,
            @RequestParam(name = "files", required = false) List<MultipartFile> files
    ) {
        ApiResponse<ReviewResponse> api = new ApiResponse<>();
        try {
            CreateReviewRequest req = new CreateReviewRequest();
            req.setOrderId(orderId);
            req.setRating(rating);
            req.setComment(comment);
            ReviewResponse resp = reviewService.createReview(cosplayerId, req, files);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (IllegalArgumentException ex) {
            api.setCode(400);
            api.setMessage(ex.getMessage());
            return ResponseEntity.badRequest().body(api);
        } catch (Exception ex) {
            api.setCode(500);
            api.setMessage("Failed to create review: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getByOrder(@PathVariable Integer orderId) {
        ApiResponse<List<ReviewResponse>> api = new ApiResponse<>();
        try {
            List<ReviewResponse> resp = reviewService.getByOrderId(orderId);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(500);
            api.setMessage("Failed to fetch reviews: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getByProvider(@PathVariable Integer providerId) {
        ApiResponse<List<ReviewResponse>> api = new ApiResponse<>();
        try {
            List<ReviewResponse> resp = reviewService.getByProviderId(providerId);
            api.setCode(0);
            api.setMessage("OK");
            api.setResult(resp);
            return ResponseEntity.ok(api);
        } catch (Exception ex) {
            api.setCode(500);
            api.setMessage("Failed to fetch reviews: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }
}
