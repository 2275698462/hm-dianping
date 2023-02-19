package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author illusion
 * @date 2023/2/19 19:27
 */
public class SimpleRedisLock implements ILock {


    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private String name;
    private StringRedisTemplate stringRedisTemplate;


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //1.获取当前线程标识，确保唯一value，避免误删别人的锁
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //2.获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent("lock:" + name, threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //1.获取锁的key
        String key = "lock:" + name;
        //2.获取锁的线程标识,也就是唯一value
        String value = stringRedisTemplate.opsForValue().get(key);
        //3.获取当前线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //判断是否一致
        if (threadId.equals(value))
            //一致，释放锁
            stringRedisTemplate.delete(key);
    }
}
