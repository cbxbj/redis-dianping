package org.example.service.impl;

import org.example.entity.UserInfo;
import org.example.mapper.UserInfoMapper;
import org.example.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
