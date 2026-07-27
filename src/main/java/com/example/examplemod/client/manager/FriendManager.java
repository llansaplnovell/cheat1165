package com.example.examplemod.client.manager;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Case-insensitive friend list. Consumed by render/HUD modules that want to
 * highlight or hide friendly players instead of treating every nearby entity
 * the same way.
 */
public final class FriendManager {

    private final Set<String> friends = new LinkedHashSet<>();

    public boolean add(String name) {
        return friends.add(key(name));
    }

    public boolean remove(String name) {
        return friends.remove(key(name));
    }

    public boolean isFriend(String name) {
        return friends.contains(key(name));
    }

    public Set<String> getFriends() {
        return Collections.unmodifiableSet(friends);
    }

    void loadAll(Iterable<String> names) {
        friends.clear();
        for (String name : names) {
            friends.add(key(name));
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
