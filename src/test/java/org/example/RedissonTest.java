package org.example;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
public class RedissonTest {

    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    public void setUp() {
        lock = redissonClient.getLock("order");
    }

    @Test
    public void method1() {
        boolean tryLock;
        try {
            tryLock = lock.tryLock(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!tryLock) {
            log.info("获取锁失败 ... 1");
        }
        try {
            log.info("获取锁成功 ... 1");
            method2();
            log.info("开始执行业务逻辑 ... 1");
        } finally {
            log.info("准备释放锁 ... 1");
            lock.unlock();
        }
    }

    public void method2() {
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            log.info("获取锁失败 ... 2");
        }
        try {
            log.info("获取锁成功 ... 2");
            log.info("开始执行业务逻辑 ... 2");
        } finally {
            log.info("准备释放锁 ... 2");
            lock.unlock();
        }
    }
}
