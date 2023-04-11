package org.example.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * TODO 缓存穿透(数据库和redis都不存在则在redis中缓存空值)
     *
     * @param keyPrefix  key的前缀
     * @param keySuffix  要查询的值(如:id)
     * @param returnType 返回值类型
     * @param dbFallback 要执行的sql
     * @param time       超时时间
     * @param timeUnit   超时单位
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID keySuffix,
                                          Class<R> returnType, Function<ID, R> dbFallback,
                                          Long time, TimeUnit timeUnit) {
        String key = keyPrefix + keySuffix;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, returnType);
        } else if ("".equals(json)) {
            // redis中保存的是 "" ,证明数据库中也无此数据
            return null;
        }
        R r = dbFallback.apply(keySuffix);
        if (r == null) {

            // TODO 缓存穿透：数据在redis和mysql中都不存在
            // 1、缓存空对象：存在短期不一致
            // 2、布隆过滤器：将数据hash四次，将对应的索引设为1，查找时获取四次hash，然后获得索引，其中一个不为1则表示数据不存在，不太准确(可确保不存在，但不确保一定存在)
            // 3、其他解决(业务方面)：增加id复杂度、数据基础格式校验、用户权限校验
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);

            return null;
        }
        this.set(key, r, time, timeUnit);
        return r;
    }

    /**
     * TODO 缓存击穿(互斥锁)：一个被高并发访问并且缓存重建业务较复杂的key突然失效了
     *
     * @param keyPrefix  key的前缀
     * @param keySuffix  要查询的值(如:id)
     * @param lockPrefix 锁的前缀
     * @param returnType 返回值类型
     * @param dbFallback 要执行的sql
     * @param time       超时时间
     * @param timeUnit   超时单位
     */
    @SneakyThrows
    public <R, ID> R queryWithMutex(String keyPrefix, ID keySuffix, String lockPrefix,
                                    Class<R> returnType, Function<ID, R> dbFallback,
                                    Long time, TimeUnit timeUnit) {
        String key = keyPrefix + keySuffix;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, returnType);
        }

        try {
            if (!tryLock(lockPrefix + keySuffix)) {
                // 没有获取到锁,休眠一段时间再获取值
                TimeUnit.MILLISECONDS.sleep(50);
                return queryWithMutex(keyPrefix, keySuffix, lockPrefix, returnType, dbFallback, time, timeUnit);
            }

            R result = dbFallback.apply(keySuffix);
            if (result == null) {
                return null;
            }
            set(key, result, time, timeUnit);
            return result;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockPrefix + keySuffix);
        }
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * TODO 缓存击穿(逻辑过期)：一个被高并发访问并且缓存重建业务较复杂的key突然失效了
     *
     * @param keyPrefix  key的前缀
     * @param keySuffix  要查询的值(如:id)
     * @param lockPrefix 锁的前缀
     * @param returnType 返回值类型
     * @param dbFallback 要执行的sql
     * @param time       超时时间
     * @param timeUnit   超时单位
     */
    @SneakyThrows
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID keySuffix, String lockPrefix,
                                            Class<R> returnType, Function<ID, R> dbFallback,
                                            Long time, TimeUnit timeUnit) {
        String key = keyPrefix + keySuffix;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(redisDataJson)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        R result = JSONUtil.toBean((JSONObject) redisData.getData(), returnType);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 未过期
            return result;
        }

        try {
            if (tryLock(lockPrefix + keySuffix)) {
                CACHE_REBUILD_EXECUTOR.submit(() -> this.setWithLogicalExpire(key, dbFallback.apply(keySuffix), time, timeUnit));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockPrefix + keySuffix);
        }
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData(value, LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

}
