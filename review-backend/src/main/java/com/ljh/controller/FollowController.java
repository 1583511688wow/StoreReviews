package com.ljh.controller;


import com.ljh.dto.Result;
import com.ljh.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    //关注，取消关注用户功能
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow){

        return followService.follow(followUserId, isFollow);

    }

    //返回登入用户是否关注博客用户状态(true,flase)
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){



        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result commonUser(@PathVariable("id") Long id){


        return followService.commonUser(id);
    }



}
