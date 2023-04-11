package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpSession;
import org.example.dto.LoginFormDTO;
import org.example.dto.Result;
import org.example.entity.User;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
