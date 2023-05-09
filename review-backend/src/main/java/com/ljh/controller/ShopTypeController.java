package com.ljh.controller;


import cn.hutool.json.JSONUtil;
import com.ljh.dto.Result;
import com.ljh.entity.ShopType;
import com.ljh.service.IShopTypeService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static com.ljh.utils.RedisConstants.CACHE_SHOP_TYPE;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("list")
    public Result queryTypeList() {

        String shopType = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE);
        if (StringUtils.isNotBlank(shopType)){

            List<ShopType> shopTypeList = JSONUtil.toList(shopType, ShopType.class);
            return Result.ok(shopTypeList);
        }

        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();

        if (CollectionUtils.isEmpty(typeList)){

            return Result.fail("分类不存在！！");
        }

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE, JSONUtil.toJsonStr(typeList));


        System.out.println(typeList);







        return Result.ok(typeList);


    }
}
