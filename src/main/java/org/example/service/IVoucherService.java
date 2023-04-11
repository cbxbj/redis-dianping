package org.example.service;

import org.example.dto.Result;
import org.example.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);

}
