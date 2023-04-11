package org.example.utils;

import cn.hutool.core.util.IdUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private static final String KEY_PREFIX = "lock:";
    private static final String VALUE = IdUtil.getSnowflake().nextIdStr();

    private final String keySuffix;
    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String keySuffix, StringRedisTemplate stringRedisTemplate) {
        this.keySuffix = keySuffix;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeout) {
        String key = KEY_PREFIX + keySuffix;
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, VALUE, timeout, TimeUnit.SECONDS));
    }

    @Override
    public void unlock() {
        //simpleUnlock();
        luaUnlock();
    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    private void luaUnlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + keySuffix),
                VALUE);
    }

    @SuppressWarnings("unused")
    private void simpleUnlock() {
        String key = KEY_PREFIX + keySuffix;
        String value = stringRedisTemplate.opsForValue().get(key);
        if (VALUE.equals(value)) {
            stringRedisTemplate.delete(key);
        }
    }

}
