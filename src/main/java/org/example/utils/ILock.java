package org.example.utils;

public interface ILock {

    boolean tryLock(long timeout);

    void unlock();
}
