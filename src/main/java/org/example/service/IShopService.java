package org.example.service;

import org.example.dto.Result;
import org.example.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);
}
