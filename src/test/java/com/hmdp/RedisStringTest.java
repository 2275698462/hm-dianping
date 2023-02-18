package com.hmdp;

import cn.hutool.db.handler.StringHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

/**
 * @author illusion
 * @date 2023/2/16 15:32
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class RedisStringTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testString() {
        stringRedisTemplate.opsForValue().set("name", "虎哥");

        String huge = stringRedisTemplate.opsForValue().get("name");

        System.out.println(huge);
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testUser() throws Exception {
        User user = new User();
        user.setAge(123);
        user.setName("哈哈哈");

        String json = mapper.writeValueAsString(user);

        stringRedisTemplate.opsForValue().set("name", json);

        String huge = stringRedisTemplate.opsForValue().get("name");

        User user1 = mapper.readValue(huge, User.class);

        System.out.println(user1);
    }

    @Test
    public void testHash() {

    }
}
