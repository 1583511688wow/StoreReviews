package com.ljh.service.impl;

import com.ljh.entity.UserInfo;
import com.ljh.mapper.UserInfoMapper;
import com.ljh.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
