package uk.co.ticklethepanda.fitbit.client.model;

import com.google.gson.annotations.Expose;

import java.time.LocalDate;

/**
 *
 */
public class RateLimitStatus {

    @Expose
    public final int hourlyLimit;
    @Expose
    public final int remainingHits;

    @Expose
    public final LocalDate resetTime;

    private RateLimitStatus(int hourlyLimit, int remainingHits,
                            LocalDate resetTime) {
        this.hourlyLimit = hourlyLimit;
        this.remainingHits = remainingHits;
        this.resetTime = resetTime;
    }

    public boolean hasRemainingHits() {
        return this.remainingHits > 0;
    }
}
