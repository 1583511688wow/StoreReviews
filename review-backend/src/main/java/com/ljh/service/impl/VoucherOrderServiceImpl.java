package com.ljh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.ljh.dto.Result;
import com.ljh.entity.SeckillVoucher;
import com.ljh.entity.VoucherOrder;
import com.ljh.mapper.VoucherOrderMapper;
import com.ljh.service.ISeckillVoucherService;
import com.ljh.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ljh.utils.RedisIdWorker;
import com.ljh.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    String queueName = "stream.orders";


    static {

        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);

    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());

    }


    /**
     *处理存在队列的订单
     *
     */
    private class VoucherOrderHandler implements Runnable {


        /**
         *
         * 正常处理消息
         */
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams.order
                    List<MapRecord<String, Object, Object>> list2 = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())

                    );

                    //2.判断是否获取到消息
                    if (list2 == null || list2.isEmpty()) {
                        //获取失败，说明没有消息，继续重试

                        continue;
                    }

                    //3.解析消息中的订单信息
                    MapRecord<String, Object, Object> entries = list2.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.直接下单到数据库
                    createVoucherOrder(voucherOrder);

                    //5.ACK确认 SACK steram.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", entries.getId());

                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }
            }

        }

        /**
         * 处理异常消息
         */
        private void handlePendingList() {
            while (true) {
                try {
                    //1.获取pending-list信息 xreadgroup group g1 c1 count 1  streams.order 0
                    List<MapRecord<String, Object, Object>> list2 = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );

                    //2.判断是否获取到消息
                    if (list2 == null || list2.isEmpty()) {
                        //获取失败，说明pending-list没有异常消息，结束循环获取pending-list信息

                        break;
                    }

                    //3.解析消息中的订单信息

                    MapRecord<String, Object, Object> entries = list2.get(0);
                    Map<Object, Object> value = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4.直接下单到数据库
                    createVoucherOrder(voucherOrder);

                    //5.ACK确认 SACK steram.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", entries.getId());

                } catch (Exception e) {
                    log.error("处理pending-list异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }



        }



    }



    /**
     * 异步秒杀订单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        //获取用户ID
     //   Long userId = UserHolder.getUser().getId();

        String testUserId = RandomUtil.randomNumbers(6);

        //获取订单id
        long orderId2 = redisIdWorker.nextId("order");


        //执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), testUserId,
                String.valueOf(orderId2)
        );



        int resultt = result.intValue();

        //判断lua脚本结果

        if (resultt != 0) {

            //判断lua脚本返回结果不为0 没有购买资格

            return Result.fail(resultt == 1 ? "库存不足！" : "不能重复下单！");

        }

        //返回订单id
        return Result.ok(orderId2);
    }


    /**
     * 创建订单
     *
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {

        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        //获取锁对象
        // RedisLock lock = new RedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("order:" + userId);


        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //失败
            log.error("不允许重复下单！");
            return;

        }

        try {
            //查询订单
            int count = query().eq("user_id", userId).count();
            //6.2判断是否存在
            if (count > 0) {

                //用户已经购买过
                log.error("用户已经购买过一次！");
                return;

            }

            //5.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId).gt("stock", 0).update();
            if (!success) {
                //失败
                log.error("库存不足！");
                return;
            }

            save(voucherOrder);
        } catch (Exception e) {
            log.error("下单异常，操作数据库");
        } finally {
            lock.unlock();
        }


    }


//
//
//
//    //redisson锁  正常串行执行
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //尚未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//
//        //3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //已经结束
//            return Result.fail("秒杀已经结束！");
//        }
//
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            //库存不足
//            return Result.fail("库存不足！");
//        }
//
//      //  Long userId = UserHolder.getUser().getId();
//        //获取锁对象
//        // RedisLock lock = new RedisLock(stringRedisTemplate, "order:" + userId);
//        String testUserId = RandomUtil.randomNumbers(4);
//
//        Long id = Long.valueOf(testUserId);
//        RLock lock = redissonClient.getLock("order:" + id);
//
//
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //判断是否获取锁成功
//        if (!isLock) {
//            //失败
//            return Result.fail("不允许重复下单！");
//
//        }
//
//        try {
//            //获取代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//            return proxy.createVoucherOrder(voucherId,id);
//        } finally {
//
//            lock.unlock();
//
//        }
//
//
//    }
//
//    //redisson锁  正常串行执行
//    @Override
//   @Transactional
//    public Result createVoucherOrder(Long voucherId,Long id) {
//        //6.一人一单
////        Long userId = UserHolder.getUser().getId();
//        //6.1查询订单
//        Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
//        //6.2判断是否存在
//        if(count > 0){
//
//            //用户已经购买过
//            return  Result.fail("用户已经购买过一次！");
//
//        }
//
//        //5.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).gt("stock", 0).update();
//        if (!success) {
//            //失败
//            return Result.fail("库存不足！");
//        }
//
//
//        //7.创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //7.1订单Id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //7.2用户id
//
//        voucherOrder.setUserId(id);
//        //7.3代金卷id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        return Result.ok(orderId);
//    }






}


