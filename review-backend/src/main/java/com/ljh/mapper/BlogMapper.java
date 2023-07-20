package com.ljh.mapper;

import com.ljh.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ljh.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;


public interface BlogMapper extends BaseMapper<Blog> {

    List<Blog> queryVoucherOfShop();

}
