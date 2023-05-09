package com.ljh.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {

        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public RedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        //获取线程标示
        String threaId = ID_PREFIX + Thread.currentThread().getId();

        //获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threaId + "" , timeoutSec, TimeUnit.SECONDS);


        return Boolean.TRUE.equals(success);
    }



    @Override
    public void unLock(){

        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name)
        ,ID_PREFIX + Thread.currentThread().getId());

    }


/*    @Override
    public void unLock() {

        //获取当前线程表示
        String threaId = ID_PREFIX + Thread.currentThread().getId();

        //获取锁中的表示
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        //判断表示是否一致
        if (threaId.equals(id)) {

            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);


        }


    }*/
}
