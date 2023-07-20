package com.ljh.Rabbitma;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ljh.dto.MqSeckill;
import com.ljh.entity.VoucherOrder;
import com.ljh.mapper.VoucherOrderMapper;
import com.ljh.service.ISeckillVoucherService;
import com.ljh.service.IVoucherOrderService;
import com.ljh.service.impl.VoucherOrderServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.ljh.utils.MqConstants.MQ_EXCHANGE_SECKILL;
import static com.ljh.utils.MqConstants.MQ_ROUTINGKEY_SECKILL;

@Slf4j
@Service
public class MqReceiver {

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherOrderMapper voucherOrder;

    @Resource
    private IVoucherOrderService iVoucherOrderService;


    @RabbitListener(queues = MQ_ROUTINGKEY_SECKILL)
    public void createVoucherOrderOne(String message) {


        MqSeckill mqSeckill = JSON.parseObject(message, MqSeckill.class);
        Long userId = mqSeckill.getId();
        Long voucherId = mqSeckill.getVoucherId();
        Long orderId = mqSeckill.getId();
        log.info("这是一个消费者 " + orderId);

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
            int count = iVoucherOrderService.query().eq("id", orderId).count();
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

            VoucherOrder voucherOrder = new VoucherOrder();
            BeanUtils.copyProperties(mqSeckill,voucherOrder );
            iVoucherOrderService.save(voucherOrder);
        } catch (Exception e) {
            log.error("下单异常，操作数据库");
        } finally {
            lock.unlock();
        }


    }





    @RabbitListener(queues = MQ_ROUTINGKEY_SECKILL)
    public void createVoucherOrderTwo(String message){

        MqSeckill mqSeckill = JSON.parseObject(message, MqSeckill.class);
        Long userId = mqSeckill.getId();
        Long voucherId = mqSeckill.getVoucherId();
        Long orderId = mqSeckill.getId();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        log.info("这是二个消费者 " + orderId);

        iVoucherOrderService.createVoucherOrder(voucherOrder);

    }






}
