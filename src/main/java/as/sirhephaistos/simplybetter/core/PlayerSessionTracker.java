package as.sirhephaistos.simplybetter.core;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSessionTracker {
    private final Map<String, Instant> joinTimes = new ConcurrentHashMap<>();

    public void onJoin(String uuid) {
        joinTimes.put(uuid, Instant.now());
    }

    public long onLeave(String uuid) {
        Instant joined = joinTimes.remove(uuid);
        if (joined == null) return 0L;
        return java.time.Duration.between(joined, Instant.now()).getSeconds();
    }
}
