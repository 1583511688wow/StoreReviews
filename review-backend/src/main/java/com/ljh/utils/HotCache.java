package com.ljh.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ljh.dto.Result;
import com.ljh.entity.Blog;
import com.ljh.mapper.BlogMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.ljh.utils.RedisConstants.*;

/**
 *缓存计算热点文章
 *
 */

@Component
public class HotCache {


    @Resource
    private RedissonClient redissonClient;

    @Resource
    private BlogMapper blogMapper;


    @Resource
    private CacheClient cacheClient;


    @Scheduled(cron = "0 0 0 * * ?")
    public Boolean queryHotBlog(Integer current) {


        RLock lock = redissonClient.getLock(BLOG_HOT_LOCK);
        String key = BLOG_HOT_KEY ;
        try {


            boolean lock1 = lock.tryLock(0, -1, TimeUnit.MINUTES);
            if (!lock1){
                return false;
            }


            //查询前5天的文章数据
            List<Blog> blogs = blogMapper.queryVoucherOfShop();


            //计算分值

            blogs.forEach(blog -> {
                int score = this.computeScore(blog);
                blog.setScore(score);

            });

            List<Blog> blogList = blogs.stream().sorted(
                    Comparator.comparing(Blog::getScore).reversed()
            ).collect(Collectors.toList());



            if (blogList.size() > 10){

                List<Blog> blogsList = blogList.subList(0, 10);
                cacheClient.set(key, blogsList, CACHE_HOT_TTL, TimeUnit.DAYS);
                return true;


            }
                cacheClient.set(key, blogList, CACHE_HOT_TTL, TimeUnit.DAYS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //只能释放线程自己的锁
            if (lock.isHeldByCurrentThread()){

                lock.unlock();
            }

        }
        return true;

    }


    private int computeScore(Blog blog){

        int scere = 0;
        if (blog.getLiked() != null) {
            scere += blog.getLiked() * 3;
        }
        if (blog.getComments() != null){

            scere += blog.getComments() * 5;
        }

        return scere;


    }



}
