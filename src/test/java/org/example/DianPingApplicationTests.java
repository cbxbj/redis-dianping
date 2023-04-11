package org.example;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Shop;
import org.example.service.IShopService;
import org.example.utils.CacheClient;
import org.example.utils.RedisConstants;
import org.example.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class DianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private IShopService shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testRedisIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        ExecutorService pool = Executors.newFixedThreadPool(500);
        long begin = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            pool.submit(() -> {
                for (int j = 0; j < 100; j++) {
                    long id = redisIdWorker.nextId("order");
                    log.info("id:{}", id);
                }
                latch.countDown();
            });
        }
        latch.await();

        log.info("time:{}", System.currentTimeMillis() - begin);
    }

    @Test
    void testSaveShop2Redis() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j;
        for (int i = 0; i < 1_000_000; i++) {
            j = i % 1_000;
            values[j] = "user_" + i;
            if (j == 999) {
                // TODO HyperLogLog 的使用
                // 内存占有不超过16k 会剔除重复数据 误差小于0.81%
                stringRedisTemplate.opsForHyperLogLog().add("hll1", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        log.info("count:{}", count);
    }
}
