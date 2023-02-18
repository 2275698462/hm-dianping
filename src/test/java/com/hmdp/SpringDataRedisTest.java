package com.hmdp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @author illusion
 * @date 2023/2/16 15:11
 */
@SpringBootTest
@RunWith(SpringRunner.class) //没有用jupiter就要加这个注解
public class SpringDataRedisTest {

    @Resource
    private RedisTemplate redisTemplate;

    @Test
    public void testString() {
        redisTemplate.opsForValue().set("name", "虎哥");
    }

    @Test
    public void test(){
        String a  = "asddsa";
        byte[] bytes = a.getBytes();
        for (byte aByte : bytes) {
            System.out.println(aByte);
        }
    }
}
