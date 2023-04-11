package org.example.service.impl;

import cn.hutool.core.util.ObjectUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.Result;
import org.example.entity.SeckillVoucher;
import org.example.entity.VoucherOrder;
import org.example.handler.VoucherOrderHandler;
import org.example.mapper.VoucherOrderMapper;
import org.example.service.ISeckillVoucherService;
import org.example.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.utils.RedisConstants;
import org.example.utils.RedisIdWorker;
import org.example.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherOrderHandler voucherOrderHandler;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(voucherOrderHandler);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        //return oldMethod(voucherId);
        return newMethod(voucherId);
    }

    private Result newMethod(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 执行lua脚本
        Long execute = stringRedisTemplate.execute(voucherOrderHandler.getVoucherOrderScript(),
                Collections.emptyList(),
                String.valueOf(voucherId), String.valueOf(userId));
        if (ObjectUtil.equals(execute, 1L)) {
            return Result.fail("库存不足");
        } else if (ObjectUtil.equals(execute, 2L)) {
            return Result.fail("该用户已购买过该优惠券");
        } else if (!ObjectUtil.equals(execute, 0L)) {
            log.error("返回值异常,返回值:{}", execute);
        }
        // 用户购秒杀优惠券成功
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 存入阻塞对列,异步执行数据库操作
        voucherOrderHandler.addOrder(voucherOrder);
        return Result.ok(orderId);
    }

    @SuppressWarnings("unused")
    private Result oldMethod(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // TODO 要先加锁再开启事务
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + UserHolder.getUser().getId());
        boolean lockResult = lock.tryLock();
        try {
            if (lockResult) {
                return applicationContext.getBean(IVoucherOrderService.class)
                        .createVoucherOrder(voucherId);
            }
        } finally {
            lock.unlock();
        }
        return Result.fail("请继续重试");
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long count = this.query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();

        if (count > 0) {
            return Result.fail("该用户已经购买过");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                // TODO 乐观锁
                // 该例子中可利用库存值是否大于0来解决
                // 但是有的只能通过数据有没有变化来判断是否安全，可采用分段锁(ConcurrentHashMap思想)
                //.eq("stock", stock)      // 理论
                .gt("stock", 0) // 工程
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
