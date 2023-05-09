package com.ljh;

import com.alibaba.fastjson.JSON;
import com.ljh.service.impl.ShopServiceImpl;
import com.ljh.service.impl.UserServiceImpl;
import com.ljh.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class MyApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private UserServiceImpl userServiceImpl;

    private ExecutorService es = Executors.newFixedThreadPool(500);


    @Test

    void testSaveShop() throws InterruptedException {


        shopService.saveShopRedis(1L,10L);
        shopService.saveShopRedis(2L,10L);
    }

    @Test
    void testWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () -> {
          for (int i = 1; i < 100; i++ ){
              long id = redisIdWorker.nextId("order");
              System.out.println("id = " + id);

          }
          countDownLatch.countDown();

        };
        long l = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        countDownLatch.await();
        long l1 = System.currentTimeMillis();
        System.out.println("time = " + (l1 - l));

    }

    //向redis添加大量数据
    @Test

    void saveTest() throws InterruptedException {

        //hash:245.38M   string:245.46M


        // 创建Jedis对象并连接到Redis
        Jedis jedis = new Jedis("192.168.123.4", 6379);
        jedis.auth("12052497");
        long startTime = System.currentTimeMillis();

        // 创建Pipeline对象
        Pipeline pipeline = jedis.pipelined();

        // 添加10000000条hash类型的数据
        for (int i = 0; i < 1000000; i++) {
            String key = "hash" + i;
//            Map<String, String> map = new HashMap<>();
//            map.put("field1", "value1");
//            map.put("field2", "value1");
//            map.put("field3", "value2");
//            map.put("field4", "value1");
//            map.put("field5", "value1");
//            map.put("field6", "value1");
//            map.put("field7", "value1");
//            map.put("field8", "value1");
//            map.put("field9", "value1");
//            map.put("field10", "value1");
//            pipeline.hmset(key, map);
            Usert usert = new Usert();
            usert.setField1("value1");
            usert.setField2("value1");
            usert.setField3("value1");
            usert.setField4("value1");
            usert.setField5("value1");
            usert.setField6("value1");
            usert.setField7("value1");
            usert.setField8("value1");
            usert.setField9("value1");
            usert.setField10("value1");


            String value = JSON.toJSONString(usert);
            pipeline.set(key, value);

        }

        // 执行所有命令
        pipeline.sync();

        // 关闭连接
        jedis.close();
        long endTime = System.currentTimeMillis();
        System.out.println(endTime - startTime);

    }
    }










