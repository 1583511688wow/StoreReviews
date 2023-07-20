package com.ljh.aop;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.ljh.annotation.HotCheck;
import com.ljh.dto.Result;
import com.ljh.dto.UserDTO;
import com.ljh.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * 用户关注AOP日志
 *
 * @author ljh
 */
@Aspect
@Component
@Slf4j
public class HotLogInfo {



    /**
     * 执行拦截
     *
     * @param joinPoint
     * @param authCheck
     * @return
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, HotCheck
            authCheck) throws Throwable {

        // 计时
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();


        // 获取请求路径
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 生成请求唯一 id
        String requestId = UUID.randomUUID().toString();
        String url = httpServletRequest.getRequestURI();
        // 获取请求参数
        Object[] args = joinPoint.getArgs();
        String reqParam = "[" + StringUtils.join(args, ", ") + "]";

        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        // 输出请求日志
        log.info("request start，id: {}, userId :{}, path: {}, ip: {}, params: {}", requestId, userId, url,
                httpServletRequest.getRemoteHost(), reqParam);




        try {

            // 执行原方法
            Object result = joinPoint.proceed();
            // 输出响应日志
            stopWatch.stop();
            long totalTimeMillis = stopWatch.getTotalTimeMillis();
            log.info("request end, id: {}, cost: {}ms", requestId, totalTimeMillis);


            return result;
        } catch (Throwable e) {

            log.error("request start，id: {}, userId :{}, path: {}, ip: {}, params: {}, error_doAround: {}",
                    requestId, userId, url, httpServletRequest.getRemoteHost(), reqParam, e);
            return Result.fail("发生异常！！!");

        }



    }
}

