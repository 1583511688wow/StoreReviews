package com.ljh.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ljh.dto.Result;
import com.ljh.dto.UserDTO;
import com.ljh.entity.Blog;
import com.ljh.service.IBlogService;
import com.ljh.utils.SystemConstants;
import com.ljh.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    //发博客
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {

        return blogService.saveBlog(blog);
    }

    //博客点赞
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {

        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    //分页展示
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    //查看博客
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id){


        return blogService.queryBlogById(id);

    }

    //点赞显示排行
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id){


        return blogService.queryBlogLikes(id);
    }

    //查看博客用户的主页，返回博客笔记信息
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }


    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset){


        return blogService.queryBlogOfFollow(max, offset);
    }



}
