package com.ljh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ljh.dto.Result;
import com.ljh.dto.UserDTO;
import com.ljh.entity.Follow;
import com.ljh.mapper.FollowMapper;
import com.ljh.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ljh.service.IUserService;
import com.ljh.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ljh.utils.RedisConstants.USER_COMMON_KEY;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {


        //1.获取登入用户ID
        Long userId = UserHolder.getUser().getId();

        String key = USER_COMMON_KEY + userId;
        //2.判断关注状态
        if (isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);

            if (save(follow)) {
                //存放在redis set
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());

            }

        }else {
            //取消关注，删除数据

            Boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id", followUserId));

            if (isSuccess){

                //redis中删除关注用户数据
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {

        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();


        return Result.ok(count > 0);
    }

    @Override
    public Result commonUser(Long id) {

        //登入用户id
        Long userId = UserHolder.getUser().getId();
        String key = USER_COMMON_KEY + userId;

        //求交集
        String key2 = USER_COMMON_KEY + id;
        Set<String> commonUserId = stringRedisTemplate.opsForSet().intersect(key, key2);

        if (commonUserId == null || commonUserId.isEmpty() ){

            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> list = commonUserId.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOS = userService.listByIds(list).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(userDTOS);
    }
}
