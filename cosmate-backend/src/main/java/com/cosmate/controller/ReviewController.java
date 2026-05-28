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
import com.cosmate.dto.request.ProviderReplyRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @GetMapping("/costume/{costumeId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getByCostume(@PathVariable Integer costumeId) {
        ApiResponse<List<ReviewResponse>> api = new ApiResponse<>();
        try {
            List<ReviewResponse> resp = reviewService.getByCostumeId(costumeId);
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

    @PutMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> replyToReview(
            @PathVariable("id") Integer id,
            @RequestBody ProviderReplyRequest request
    ) {
        ApiResponse<ReviewResponse> api = new ApiResponse<>();
        try {
            // Get provider id from security context. If not available, use dummy (e.g., 0)
            Integer providerId = getCurrentUserId();
            if (providerId == null) {
                // For now we can set a dummy provider id or return unauthorized
                api.setCode(401);
                api.setMessage("Unauthenticated");
                return ResponseEntity.status(401).body(api);
            }

            ReviewResponse resp = reviewService.replyToReview(id, providerId, request);
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
            api.setMessage("Failed to reply to review: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    @PutMapping("/{id}/toggle-toxic")
    public ResponseEntity<ApiResponse<ReviewResponse>> toggleToxicStatus(@PathVariable("id") Integer id) {
        ApiResponse<ReviewResponse> api = new ApiResponse<>();
        try {
            ReviewResponse resp = reviewService.toggleToxicStatus(id);
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
            api.setMessage("Failed to toggle toxic status: " + ex.getMessage());
            return ResponseEntity.status(500).body(api);
        }
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        try {
            if (principal instanceof String) {
                String s = (String) principal;
                if (s.equalsIgnoreCase("anonymousUser")) return null;
                return Integer.valueOf(s);
            }
            if (principal instanceof Integer) return (Integer) principal;
            if (principal instanceof Long) return ((Long) principal).intValue();
            return Integer.valueOf(principal.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
