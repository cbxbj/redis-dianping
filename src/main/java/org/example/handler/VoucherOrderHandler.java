package org.example.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.VoucherOrder;
import org.example.service.ISeckillVoucherService;
import org.example.service.IVoucherOrderService;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
@Component
public class VoucherOrderHandler implements Runnable {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    private static final BlockingQueue<VoucherOrder> orders = new ArrayBlockingQueue<>(1024 * 1024);

    private static final DefaultRedisScript<Long> VOUCHER_ORDER_SCRIPT = new DefaultRedisScript<>();

    static {
        VOUCHER_ORDER_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        VOUCHER_ORDER_SCRIPT.setResultType(Long.class);
    }

    public void addOrder(VoucherOrder voucherOrder) {
        orders.add(voucherOrder);
    }

    public DefaultRedisScript<Long> getVoucherOrderScript() {
        return VOUCHER_ORDER_SCRIPT;
    }

    @Override
    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            try {
                VoucherOrder voucherOrder = orders.take();
                applicationContext.getBean(VoucherOrderHandler.class)
                        .handlerVoucherOrder(voucherOrder);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                log.error("e:", e);
            }
        }
    }

    @Transactional
    public void handlerVoucherOrder(VoucherOrder voucherOrder) {
        boolean updateResult = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!updateResult) {
            log.error("[handlerVoucherOrder]扣减秒杀优惠券库存失败,voucherOrder:{}", voucherOrder);
        } else {
            IVoucherOrderService orderService = applicationContext.getBean(IVoucherOrderService.class);
            boolean saveResult = orderService.save(voucherOrder);
            if (!saveResult) {
                log.error("[handlerVoucherOrder]添加秒杀优惠券订单,voucherOrder:{}", voucherOrder);
            }
        }
    }
}
