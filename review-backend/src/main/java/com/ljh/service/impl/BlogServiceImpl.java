package com.ljh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ljh.dto.Result;
import com.ljh.dto.ScrollResult;
import com.ljh.dto.UserDTO;
import com.ljh.entity.Blog;
import com.ljh.entity.Follow;
import com.ljh.entity.User;
import com.ljh.mapper.BlogMapper;
import com.ljh.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ljh.service.IFollowService;
import com.ljh.service.IUserService;
import com.ljh.utils.SystemConstants;
import com.ljh.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ljh.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.ljh.utils.RedisConstants.FEED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    public  StringRedisTemplate stringRedisTemplate;

    @Resource
    public IFollowService followService;


    //查看博客，根据博客ID查询，但只有用户id信息
    @Override
    public Result queryBlogById(Long id) {
        //1.查询bLog
        Blog blog = getById(id);
        if (blog ==null){

            return Result.fail("博客不存在！");
        }

        queryBlogUser(blog);

        isBlogLiked(blog);

        return Result.ok(blog);
    }

    //查询博客用户信息并添加用户头像，名字
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


    //判断用户是否点赞过 为blog添加点赞状态
    private void isBlogLiked(Blog blog){
        //1.获取当前用户
        UserDTO userDto = UserHolder.getUser();
        if (userDto == null){

            return ;
        }

        Long userId = userDto.getId();
        //2.判断是否已点赞

        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
        
    }


    //分页查询
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);

        });
        return Result.ok(records);
    }



    //点赞
    @Override
    public Result likeBlog(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();

        //2.判断是否已点赞

        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //判断是否点赞过
        if (score ==null){

            //3.未点赞，点赞+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {

                //3.1保存在redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());

                 }
            }else {
            //4.已点赞，点赞-1
                boolean isSuccess = update().setSql("liked = liked -1").eq("id", id).update();

                if (isSuccess){

                    stringRedisTemplate.opsForZSet().remove(key, userId.toString());

                }

            }

            return Result.ok();
        }


    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;

        //在redis查讯top5用户 zrange key 0 4
        Set<String> stringSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if (stringSet ==null || stringSet.isEmpty()){

            return Result.ok(Collections.emptyList());
        }

        //解析用户id
        List<Long> ids = stringSet.stream().map(Long::valueOf).collect(Collectors.toList());

        //查询用户
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query().in("id", ids)
                .last("ORDER  BY FIELD(id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());



        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog)
    {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){

            return Result.fail("发表，保存笔记失败！");
        }
        //查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        System.out.println(follows);
        //推送笔记ID给所有粉丝
        for (Follow follow : follows) {
            //4.1获取粉丝id
            Long userId = follow.getUserId();
            //推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId()
                    .toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        //2.查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        //非空判断
        if (typedTuples ==null || typedTuples.isEmpty()){

            return Result.ok();
        }


        ArrayList<Object> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int offSet = 1;//
        //3.解析:blogId minTime(时间戳) offset(上一次最小时间戳重复的个数)
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取笔记id
            ids.add(Long.valueOf(typedTuple.getValue()));

            //获取分数(时间戳)
             long time = typedTuple.getScore().longValue();

             if (minTime == time){

                 offSet++;
             }else {
                 minTime = time;
                 offSet = 1;
             }


        }

             //根据id查询blog
            String idStr = StrUtil.join(",", ids);
        System.out.println(idStr);
            List<Blog> blogs = query().in("id", ids)
                    .last("ORDER  BY FIELD(id, " + idStr + ")").list();

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);

        }


            //封装结果
            ScrollResult r = new ScrollResult();
            r.setList(blogs);
            r.setMinTime(minTime);
            r.setOffset(offSet);


            return Result.ok(r);

        }









}

