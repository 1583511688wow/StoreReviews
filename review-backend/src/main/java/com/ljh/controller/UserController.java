package com.ljh.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ljh.dto.LoginFormDTO;
import com.ljh.dto.Result;
import com.ljh.dto.UserDTO;
import com.ljh.entity.User;
import com.ljh.entity.UserInfo;
import com.ljh.service.IUserInfoService;
import com.ljh.service.IUserService;
import com.ljh.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static com.ljh.utils.RedisConstants.LOGIN_USER_KEY;

/**
 *用户管理
 *
 * @author 李俊豪
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */

    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能


        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){

        // 1.获取请求头中的token
        String token = request.getHeader("authorization");
        // 2.基于TOKEN获取redis中的用户
        String userKey  = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(userKey);
        UserHolder.removeUser();

        return Result.ok("退出成功！");
    }

    @GetMapping("/me")
    public Result me(){

        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }




    //查看博客用户主页，返回用户信息
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    //签到
    @PostMapping("/sign")
    public Result sign(){


        return userService.sign();
    }


    @GetMapping("/sign/count")
    public Result signCount(){

        return userService.signCount();
    }





}
