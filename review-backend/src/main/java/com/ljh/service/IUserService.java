package com.ljh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ljh.dto.LoginFormDTO;
import com.ljh.dto.Result;
import com.ljh.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}
