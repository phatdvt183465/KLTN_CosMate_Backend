package com.cosmate.service.impl;

import com.cosmate.dto.request.CreateReviewRequest;
import com.cosmate.dto.response.ReviewResponse;
import com.cosmate.dto.response.ReviewUrlResponse;
import com.cosmate.entity.Order;
import com.cosmate.entity.Review;
import com.cosmate.entity.ReviewUrl;
import com.cosmate.repository.OrderRepository;
import com.cosmate.repository.ReviewRepository;
import com.cosmate.repository.ReviewUrlRepository;
import com.cosmate.service.FirebaseStorageService;
import com.cosmate.service.ReviewService;
import com.cosmate.service.AIService;
import com.cosmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewUrlRepository reviewUrlRepository;
    private final OrderRepository orderRepository;
    private final FirebaseStorageService firebaseStorageService;
    private final com.cosmate.service.ProviderService providerService;
    private final UserRepository userRepository;
    private final AIService aiService;
    private final com.cosmate.repository.ProviderRepository providerRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(Integer cosplayerId, CreateReviewRequest request, List<MultipartFile> files) {
        if (request == null) throw new IllegalArgumentException("Missing review data");
        Integer orderId = request.getOrderId();
        if (orderId == null) throw new IllegalArgumentException("orderId is required");
        Integer rating = request.getRating();
        if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("rating must be between 1 and 5");
        if (request.getComment() != null && request.getComment().length() > 1000) {
            throw new IllegalArgumentException("Đánh giá không được vượt quá 1000 ký tự");
        }

        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) throw new IllegalArgumentException("Order not found");
        Order order = opt.get();

        if (order.getCosplayerId() == null || !order.getCosplayerId().equals(cosplayerId)) {
            throw new IllegalArgumentException("Not order owner");
        }

        if (!"COMPLETED".equals(order.getStatus())) {
            throw new IllegalArgumentException("Order not completed");
        }

        // enforce one review per order
        List<Review> existing = reviewRepository.findByOrderId(orderId);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalArgumentException("Review already exists for this order");
        }

        Review r = Review.builder()
                .order(order)
                .rating(rating)
                .comment(request.getComment())
                .build();
        r = reviewRepository.save(r);

        // update provider totals (average rating and review count)
        try {
            if (order.getProviderId() != null) {
                providerService.addReviewRating(order.getProviderId(), rating);
            }
        } catch (Exception ignored) {
        }

        List<ReviewUrlResponse> images = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) continue;
                String destination = String.format("reviews/%d/%d_%s", orderId, System.currentTimeMillis(), f.getOriginalFilename());
                String url = firebaseStorageService.uploadFile(f, destination);
                ReviewUrl ru = ReviewUrl.builder().review(r).url(url).build();
                ru = reviewUrlRepository.save(ru);
                ReviewUrlResponse rur = new ReviewUrlResponse();
                rur.setId(ru.getId());
                rur.setUrl(ru.getUrl());
                images.add(rur);
            }
        }

        ReviewResponse resp = new ReviewResponse();
        resp.setId(r.getId());
        resp.setOrderId(orderId);
        resp.setRating(r.getRating());
        resp.setComment(r.getComment());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setImages(images);
        // set username of reviewer (cosplayer)
        if (order.getCosplayerId() != null) {
            userRepository.findById(order.getCosplayerId()).ifPresent(u -> {
                resp.setUsername(u.getUsername());
                resp.setAvatarUrl(u.getAvatarUrl());
            });
        }

        // Kích hoạt AI phân tích review chạy ngầm
        String aiComment = "Đánh giá " + rating + " sao. Nội dung: " + (request.getComment() != null ? request.getComment() : "");
        aiService.analyzeReviewAsync(r.getId(), aiComment);

        return resp;
    }

    @Override
    public List<ReviewResponse> getByOrderId(Integer orderId) {
        List<Review> reviews = reviewRepository.findByOrderId(orderId);
        List<ReviewResponse> out = new ArrayList<>();
        if (reviews == null || reviews.isEmpty()) return out;
        for (Review r : reviews) {
            ReviewResponse resp = mapToDto(r);
            out.add(resp);
        }
        return out;
    }

    @Override
    public List<ReviewResponse> getByProviderId(Integer providerId) {
        List<Review> reviews = reviewRepository.findByOrderProviderId(providerId);
        List<ReviewResponse> out = new ArrayList<>();
        if (reviews == null || reviews.isEmpty()) return out;
        for (Review r : reviews) {
            ReviewResponse resp = mapToDto(r);
            out.add(resp);
        }
        return out;
    }

    @Override
    public List<ReviewResponse> getByCostumeId(Integer costumeId) {
        List<Review> reviews = reviewRepository.findByCostumeId(costumeId);
        List<ReviewResponse> out = new ArrayList<>();
        if (reviews == null || reviews.isEmpty()) return out;
        for (Review r : reviews) {
            ReviewResponse resp = mapToDto(r);
            out.add(resp);
        }
        return out;
    }

    private ReviewResponse mapToDto(Review r) {
        ReviewResponse resp = new ReviewResponse();
        resp.setId(r.getId());
        resp.setOrderId(r.getOrder() != null ? r.getOrder().getId() : null);
        resp.setRating(r.getRating());
        resp.setComment(r.getComment());
        resp.setCreatedAt(r.getCreatedAt());
        resp.setAiSentiment(r.getAiSentiment());
        resp.setIsSpamOrToxic(r.getIsSpamOrToxic());
        resp.setAiSummary(r.getAiSummary());
        resp.setIsConflicting(r.getIsConflicting());

        List<ReviewUrlResponse> images = new ArrayList<>();
        List<ReviewUrl> urls = reviewUrlRepository.findByReviewId(r.getId());
        if (urls != null) {
            for (ReviewUrl ru : urls) {
                ReviewUrlResponse rur = new ReviewUrlResponse();
                rur.setId(ru.getId());
                rur.setUrl(ru.getUrl());
                images.add(rur);
            }
        }
        resp.setImages(images);
        // populate username from order->cosplayerId
        Integer cosplayerId = r.getOrder() != null ? r.getOrder().getCosplayerId() : null;
        if (cosplayerId != null) {
            userRepository.findById(cosplayerId).ifPresent(u -> {
                resp.setUsername(u.getUsername());
                resp.setAvatarUrl(u.getAvatarUrl());
            });
        }
        // populate provider reply fields
        resp.setProviderReply(r.getProviderReply());
        resp.setRepliedAt(r.getRepliedAt());
        resp.setRepliedByProviderId(r.getRepliedByProviderId());
        return resp;
    }

    @Override
    public ReviewResponse replyToReview(Integer reviewId, Integer providerId, com.cosmate.dto.request.ProviderReplyRequest request) {
        if (reviewId == null) throw new IllegalArgumentException("reviewId is required");
        if (providerId == null) throw new IllegalArgumentException("providerId is required");
        if (request == null || request.getReplyContent() == null) throw new IllegalArgumentException("reply content is required");

        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("Review not found"));

        review.setProviderReply(request.getReplyContent());
        review.setRepliedAt(java.time.LocalDateTime.now());
        review.setRepliedByProviderId(providerId);

        review = reviewRepository.save(review);

        return mapToDto(review);
    }

    @Override
    @Transactional
    public ReviewResponse toggleToxicStatus(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        boolean currentStatus = review.getIsSpamOrToxic() != null ? review.getIsSpamOrToxic() : false;
        review.setIsSpamOrToxic(!currentStatus);
        review = reviewRepository.save(review);

        if (review.getOrder() != null && review.getOrder().getProviderId() != null) {
            recalculateProviderRating(review.getOrder().getProviderId());
        }

        return mapToDto(review);
    }

    private void recalculateProviderRating(Integer providerId) {
        List<Review> reviews = reviewRepository.findByOrderProviderId(providerId);
        List<Review> validReviews = reviews.stream()
                .filter(r -> (r.getIsSpamOrToxic() == null || !r.getIsSpamOrToxic())
                          && (r.getIsConflicting() == null || !r.getIsConflicting()))
                .toList();

        int totalReviews = validReviews.size();
        final double average = totalReviews > 0
                ? validReviews.stream().mapToDouble(Review::getRating).sum() / totalReviews
                : 0.0;

        providerRepository.findById(providerId).ifPresent(provider -> {
            provider.setTotalReviews(totalReviews);
            provider.setTotalRating(java.math.BigDecimal.valueOf(average));
            providerRepository.save(provider);
        });
    }

    @Override
    public List<ReviewResponse> getAllReviews() {
        List<Review> reviews = reviewRepository.findAll();
        List<ReviewResponse> out = new ArrayList<>();
        if (reviews == null || reviews.isEmpty()) return out;
        for (Review r : reviews) {
            ReviewResponse resp = mapToDto(r);
            out.add(resp);
        }
        return out;
    }
}
