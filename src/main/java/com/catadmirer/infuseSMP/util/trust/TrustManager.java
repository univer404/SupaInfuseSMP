package com.catadmirer.infuseSMP.util.trust;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns each player's trusted UUID list and keeps trust checks consistent across abilities. */
public final class TrustManager {
    private final Map<UUID, Set<UUID>> trustedPlayers = new HashMap<>();

    public Set<UUID> getTrusted(UUID owner) {
        return trustedPlayers.getOrDefault(owner, Set.of());
    }

    public Set<UUID> getOrCreate(UUID owner) {
        return trustedPlayers.computeIfAbsent(owner, ignored -> new HashSet<>());
    }

    public void setTrusted(UUID owner, Collection<UUID> players) {
        trustedPlayers.put(owner, new HashSet<>(players));
    }

    public boolean isTrusted(UUID owner, UUID target) {
        return getTrusted(owner).contains(target);
    }

    public Set<UUID> playerIds() {
        return Set.copyOf(trustedPlayers.keySet());
    }

    public void clear(UUID owner) {
        getOrCreate(owner).clear();
    }
}
