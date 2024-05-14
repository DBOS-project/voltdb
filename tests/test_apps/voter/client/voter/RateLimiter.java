package voter;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {
    private final long maxTokens;
    private final long refillRate; // tokens added per millisecond
    private final AtomicLong tokensAvailable;
    private final AtomicLong lastRefillTimestamp;

    public RateLimiter(long maxRequestsPerSecond) {
        this.maxTokens = maxRequestsPerSecond;
        this.refillRate = maxRequestsPerSecond / 1000L; // convert per second rate to per millisecond rate
        this.tokensAvailable = new AtomicLong(0);
        this.lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());
    }

    public boolean tryAcquire() {
        while (true) {
            long currentTokens = tokensAvailable.get();
            if (currentTokens > 0) {
                if (tokensAvailable.compareAndSet(currentTokens, currentTokens - 1)) {
                    return true;
                }
            } else {
                refill();
                return false;
            }
        }
    }

    public void acquire() {
        while (tryAcquire() == false) {
            try {
                Thread.sleep(1);
                //LockSupport.parkNanos(500_000); // 500 microseconds
            } catch (Exception e) {
                
            }
        }
    }

    private void refill() {
        long now = System.currentTimeMillis();
        long lastRefillTime = lastRefillTimestamp.get();
        long elapsedTime = now - lastRefillTime;

        if (elapsedTime > 0) {
            long tokensToAdd = elapsedTime * refillRate;
            long adjustedTokens = Math.min(maxTokens, tokensAvailable.get() + tokensToAdd);
            if (tokensAvailable.compareAndSet(tokensAvailable.get(), adjustedTokens)) {
                lastRefillTimestamp.compareAndSet(lastRefillTime, now);
            }
        }
    }
}
