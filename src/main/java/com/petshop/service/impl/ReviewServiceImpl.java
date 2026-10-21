package com.petshop.service.impl;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import com.petshop.repository.ReviewRepository;
import com.petshop.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<Review> searchReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable) {
        String safeKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return reviewRepository.searchReviewsAdmin(safeKeyword, rating, status, pageable);
    }

    @Override
    @Transactional
    public Review toggleReviewStatus(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với mã #" + reviewId));

        if (review.getStatus() == ReviewStatus.VISIBLE) {
            review.setStatus(ReviewStatus.HIDDEN);
            log.info("Review #{} hidden by admin", reviewId);
        } else {
            review.setStatus(ReviewStatus.VISIBLE);
            log.info("Review #{} visible by admin", reviewId);
        }

        return reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Review getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đánh giá với mã #" + reviewId));
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalReviewsCount() {
        return reviewRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long getVisibleReviewsCount() {
        return reviewRepository.countByStatus(ReviewStatus.VISIBLE);
    }

    @Override
    @Transactional(readOnly = true)
    public long getHiddenReviewsCount() {
        return reviewRepository.countByStatus(ReviewStatus.HIDDEN);
    }

    @Override
    @Transactional(readOnly = true)
    public Double getAverageRating() {
        return reviewRepository.getOverallAverageRating();
    }
}
