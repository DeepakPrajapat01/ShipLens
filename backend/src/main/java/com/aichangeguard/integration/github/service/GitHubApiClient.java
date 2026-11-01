package com.aichangeguard.integration.github.service;

import com.aichangeguard.integration.github.config.GitHubAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for communicating with GitHub REST API with rate limit monitoring and error handling.
 */
@Service
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final GitHubAppProperties properties;
    private final GitHubAppTokenService tokenService;

    // Rate limit tracking
    private final AtomicInteger rateLimitRemaining = new AtomicInteger(5000);
    private final AtomicInteger rateLimitTotal = new AtomicInteger(5000);
    private final AtomicLong rateLimitResetEpoch = new AtomicLong(0);

    public GitHubApiClient(GitHubAppProperties properties, GitHubAppTokenService tokenService) {
        this.properties = properties;
        this.tokenService = tokenService;
    }

    /**
     * Checks whether GitHub API calls should be deferred due to rate limit exhaustion.
     */
    public boolean isRateLimited() {
        int remaining = rateLimitRemaining.get();
        long resetEpoch = rateLimitResetEpoch.get();
        long now = Instant.now().getEpochSecond();

        if (remaining <= properties.getRateLimitThreshold()) {
            if (now < resetEpoch) {
                log.warn("GitHub API rate limit threshold reached: {} remaining (resets in {}s)",
                        remaining, resetEpoch - now);
                return true;
            }
        }
        return false;
    }

    /**
     * Updates recorded rate limit from GitHub response headers.
     */
    public void recordRateLimitHeaders(String limitHeader, String remainingHeader, String resetHeader) {
        try {
            if (limitHeader != null) {
                rateLimitTotal.set(Integer.parseInt(limitHeader));
            }
            if (remainingHeader != null) {
                rateLimitRemaining.set(Integer.parseInt(remainingHeader));
            }
            if (resetHeader != null) {
                rateLimitResetEpoch.set(Long.parseLong(resetHeader));
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse GitHub rate limit headers: {}", e.getMessage());
        }
    }

    public RateLimitInfo getRateLimitInfo() {
        return new RateLimitInfo(
                rateLimitTotal.get(),
                rateLimitRemaining.get(),
                Instant.ofEpochSecond(rateLimitResetEpoch.get()),
                isRateLimited()
        );
    }

    public record RateLimitInfo(
            int totalLimit,
            int remaining,
            Instant resetAt,
            boolean isThrottled
    ) {}
}

