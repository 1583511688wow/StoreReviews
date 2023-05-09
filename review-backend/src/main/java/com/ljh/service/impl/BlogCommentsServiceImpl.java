package com.ljh.service.impl;

import com.ljh.entity.BlogComments;
import com.ljh.mapper.BlogCommentsMapper;
import com.ljh.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
