package org.sempiria.cepheuna.memory;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Store conversation session locks.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class MemorySessionLockStore {
    public final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public synchronized @NonNull ReentrantLock getLock(String cid) {
        return locks.computeIfAbsent(cid, (k) -> new ReentrantLock());
    }

    public synchronized void lock(String cid) {
        getLock(cid).lock();
    }

    public synchronized void unlock(String cid) {
        getLock(cid).unlock();
    }
}
