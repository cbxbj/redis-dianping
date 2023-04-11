package org.example.service.impl;

import org.example.entity.ShopType;
import org.example.mapper.ShopTypeMapper;
import org.example.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
