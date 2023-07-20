package com.ljh.Rabbitma;


import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.ljh.utils.MqConstants.MQ_EXCHANGE_SECKILL;
import static com.ljh.utils.MqConstants.MQ_ROUTINGKEY_SECKILL;

@Slf4j
@Service
public class MqSender {

    @Resource
    private RabbitTemplate rabbitTemplate;


    /**
     *
     * 发送秒杀信息
     * @param message
     */
    public void sendSeckillMessage(String message){

        log.info("发送消息：" + message);
        rabbitTemplate.convertAndSend(MQ_EXCHANGE_SECKILL, MQ_ROUTINGKEY_SECKILL, message);


    }

}
