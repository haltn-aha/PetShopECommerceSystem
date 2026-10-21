package com.petshop.service;

import com.petshop.entity.Review;
import com.petshop.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    Page<Review> searchReviews(String keyword, Integer rating, ReviewStatus status, Pageable pageable);
    Review toggleReviewStatus(Long reviewId);
    Review getReviewById(Long reviewId);
    long getTotalReviewsCount();
    long getVisibleReviewsCount();
    long getHiddenReviewsCount();
    Double getAverageRating();
}
