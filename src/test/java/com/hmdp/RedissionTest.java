package com.hmdp;


import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author illusion
 * @date 2023/2/20 14:45
 */
@SuppressWarnings("ALl")
@SpringBootTest
public class RedissionTest {

    @Resource
    private RedissonClient redissonClient;

    @Test
    void testRedission() throws InterruptedException {
        //获取锁（可重试），指定锁名称
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，尝试分别是：等待时间，期间会重试、自动释放时间、时间单位
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        //判断是否获取锁成功
        if (isLock){
            try {
                System.out.println("执行业务");
            } finally {
                //释放锁
                lock.unlock();
            }

        }
    }
}
