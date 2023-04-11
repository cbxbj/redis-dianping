package org.example.service.impl;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.example.dto.Result;
import org.example.entity.Shop;
import org.example.mapper.ShopMapper;
import org.example.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.utils.CacheClient;
import org.example.utils.RedisConstants;
import org.example.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * TODO：try-catch-finally
 * finally中 存在 return，不会抛异常，返回finally中值
 * finally中 不存在 return，会抛异常，若方法返回值为基本类型，finally修改了该值，则不影响返回结果；否则，会影响返回结果
 */
@Service
@SuppressWarnings("unused")
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop;
        //shop = queryWithPassThrough(id);

        // 缓存击穿(互斥锁)
        //shop = queryWithMutex(id);

        // 缓存击穿(逻辑过期)
        shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存穿透
     */
    private Shop queryWithPassThrough(Long id) {
        return cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id,
                Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

    /**
     * 缓存击穿(互斥锁)
     */
    private Shop queryWithMutex(Long id) {
        return cacheClient.queryWithMutex(RedisConstants.CACHE_SHOP_KEY, id, RedisConstants.LOCK_SHOP_KEY,
                Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

    /**
     * 缓存击穿(逻辑过期)
     */
    private Shop queryWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, RedisConstants.LOCK_SHOP_KEY,
                Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

    /**
     * 将mysql中的shop信息存放到redis中
     *
     * @param id            shop的id
     * @param expireSeconds 逻辑过期时长
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData(shop, LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // TODO 先操作数据库再删缓存
        // 先删缓存再操作数据库 -> 线程1删缓存，线程2查询到旧数据并写到缓存，线程1修改数据库
        // 先操作数据库再删缓存 -> (缓存过期)线程1查询数据库，线程2修改数据库并删除缓存，线程1再写缓存(概率极低)
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}

