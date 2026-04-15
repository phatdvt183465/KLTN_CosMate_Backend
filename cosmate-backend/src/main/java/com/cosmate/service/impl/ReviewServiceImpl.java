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

    @Override
    @Transactional
    public ReviewResponse createReview(Integer cosplayerId, CreateReviewRequest request, List<MultipartFile> files) {
        if (request == null) throw new IllegalArgumentException("Missing review data");
        Integer orderId = request.getOrderId();
        if (orderId == null) throw new IllegalArgumentException("orderId is required");
        Integer rating = request.getRating();
        if (rating == null || rating < 1 || rating > 5) throw new IllegalArgumentException("rating must be between 1 and 5");

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
            userRepository.findById(order.getCosplayerId()).ifPresent(u -> resp.setUsername(u.getUsername()));
        }

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
            userRepository.findById(cosplayerId).ifPresent(u -> resp.setUsername(u.getUsername()));
        }
        return resp;
    }
}
