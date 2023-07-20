package com.ljh.dto;

import com.ljh.entity.VoucherOrder;
import lombok.Data;

import java.io.Serializable;

@Data
public class MqSeckill  implements Serializable{


    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private  Long userId;

    /**
     * 优惠卷ID
     */

    private Long voucherId;


    /**
     * 订单ID
     */
    private Long id;
}
