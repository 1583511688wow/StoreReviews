package com.ljh.service.impl;

import cn.hutool.json.JSONUtil;
import com.ljh.dto.Result;
import com.ljh.entity.Shop;
import com.ljh.mapper.ShopMapper;
import com.ljh.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ljh.utils.CacheClient;
import com.ljh.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.ljh.utils.RedisConstants.*;


@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        //解决缓存穿透
         Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById,
        CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //互斥锁解决缓存击穿
        //Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById,
               // CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //逻 辑过期解决缓存击穿
       /* Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES);

*/
        if (shop == null){

            return Result.fail("店铺不存在！");
        }

            return Result.ok(shop);
    }

    /*    /互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id){
        Shop shop = null;
        String lockKey = "lock:shop" + id;


            //从redis查询商铺信息
            String key = CACHE_SHOP_KEY + id;
            String shopJson = stringRedisTemplate.opsForValue().get(key);


            //判断 店铺是否存在
            if (StrUtil.isNotBlank(shopJson)){

                //存在话 店铺信息shopJson 转化成shop数据返回
                shop = JSONUtil.toBean(shopJson, Shop.class);

                return shop;
            }

            //店铺不存在话，判断是否为空值
            if (shopJson != null){

                return null;
            }

            //--实现缓存重建
        try {
            //1.获取互斥锁

            boolean lock = tryLock(lockKey);

            //2.判断是否获取成功
            if (!lock){

                //3.获取失败，休眠并重试
                Thread.sleep(50);
               return   queryWithMutex(id);
            }


            //不存在 查询数据库得到店铺信息
            shop = getById(id);

            //模拟重建延迟
            Thread.sleep(200);

            if (shop == null){

                //将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }

            //店铺信息shop 转化成json数据存到redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
           throw new RuntimeException(e);
        } finally {

            //释放互斥锁
            unLock(lockKey);

        }


        return shop;


    }






    //逻辑过期解决缓存击穿
    public Shop queryWithLogicalExpire(Long id){


        //从redis查询商铺信息
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);


        //判断 店铺是否存在
        if (StrUtil.isBlank(shopJson)){

            //未命中话返回空
            return null;
        }

        //命中话，先把json序列话为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回商铺信息
            return shop;
        }

        //--过期 需要缓存重建

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean tryLock = tryLock(lockKey);

        //判断是否获取成功
        if(tryLock){

            //成功话 开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    this.saveShopRedis(id, 30L);
                } catch (Exception e) {
                    new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }


            });


        }


        //返回过期的信息
        return shop;


    }





    //缓存穿透
     public Shop queryWithPassThrough(Long id){


        //从redis查询商铺信息
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);


        //判断 店铺是否存在
        if (StrUtil.isNotBlank(shopJson)){

            //存在话 店铺信息shopJson 转化成shop数据返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);

            return shop;
        }

        //店铺不存在话，判断是否为空值
        if (shopJson != null){

            return null;
        }


        //不存在 查询数据库得到店铺信息
        Shop shop = getById(id);

        if (shop == null){

            //将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        //店铺信息shop 转化成json数据存到redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;


    }





    //获取锁
    private boolean tryLock(String key){

        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(flag);
    }

    //删除锁
    private void unLock(String key){

        stringRedisTemplate.delete(key);
    }*/

    //逻辑过期时间json
    public void saveShopRedis(Long id, Long expireSeconds) throws InterruptedException {

        Shop shop = getById(id);
        String key = CACHE_SHOP_KEY + id;
        Thread.sleep(200);


        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }


    //修改店铺信息
    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        Long id = shop.getId();

        if (id == null){
            return Result.fail("店铺id为空！");
        }

        //更新数据库
        updateById(shop);
        //删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);



        return Result.ok();
    }
}
