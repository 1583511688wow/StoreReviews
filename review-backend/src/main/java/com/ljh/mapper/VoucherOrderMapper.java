package com.ljh.mapper;

import com.ljh.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;


public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {


    int queryLastBlogs();


}
