package com.ljh.service;

import com.ljh.dto.Result;
import com.ljh.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

   // Result createVoucherOrder(Long voucherId , Long id);


    void createVoucherOrder(VoucherOrder voucherOrder);
}
