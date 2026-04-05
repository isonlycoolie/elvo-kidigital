package com.elvo.wallet.service.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;

@Component
public class WalletExecutionLockManager {

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    public ReentrantLock lock(String key) {
        ReentrantLock lock = lockMap.computeIfAbsent(key, unused -> new ReentrantLock());
        lock.lock();
        return lock;
    }

    public void unlock(String key, ReentrantLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } finally {
            if (!lock.isLocked()) {
                lockMap.remove(key, lock);
            }
        }
    }
}
