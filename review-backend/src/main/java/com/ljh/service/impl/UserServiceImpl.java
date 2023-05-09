package com.ljh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ljh.dto.LoginFormDTO;
import com.ljh.dto.Result;
import com.ljh.dto.UserDTO;
import com.ljh.entity.User;
import com.ljh.mapper.UserMapper;
import com.ljh.service.IUserService;
import com.ljh.utils.RegexUtils;
import com.ljh.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.ljh.utils.RedisConstants.*;
import static com.ljh.utils.SystemConstants.USER_NICK_NAME_PREFIX;


@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result  sendCode(String phone, HttpSession session) {
        //检验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {

            return Result.fail("手机格式错误！");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存到redis 验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);


        System.out.println("验证码为：" + code);


        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {

            return Result.fail("手机格式错误！");
        }

        //从redis 获取验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code1 = loginForm.getCode();
        if(code == null || !code.equals(code1)){

            return Result.fail("验证码错误！");
        }

        User user = query().eq("phone", phone).one();

        if (user == null){

           user = createUserWithPhone(phone);
        }


        //将用户保存到redis
        //生成key 作为口令令牌
        String token = UUID.randomUUID().toString(true);

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);

        String tokenKey =  LOGIN_USER_KEY + token;

        userMap.forEach((key, value) -> {
            if (null != value) userMap.put(key, String.valueOf(value));
        });

        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置有效期token
       stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result sign() {

        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2.获取日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.写入redis SETBIT KEY OFFSET 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);


        return Result.ok();
    }


    @Override
    public Result signCount() {

        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2.获取日期
        LocalDateTime now = LocalDateTime.now();

        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;

        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //5.获取本月 到今天截止的所有签到记录 返回的是一个十进制的的数 BITFIELD key GET u? ?
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        //判断result
        if (result == null || result.isEmpty()){

            //没有任何签到结果
            return Result.ok();
        }

        Long num = result.get(0);
        if (num == null || num == 0){

            return Result.ok();

        }

        int count = 0;//计数器
        //循环遍历
        while (true){

            //这个数做与1的与运算 得到最后一个数bit位 判断是否为0
            if ((num & 1) == 0){
                //如果为0 说明未签到 结束
                break;
            }else {
                //不为0 说明已经签到
                count++;

            }

            //把数字右移，抛弃最后一个bit位
            num >>>= 1;
        }



        return Result.ok(count);
    }

    public    User createUserWithPhone(String phone){

        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10) );

        save(user);

        return user;
    }



}
