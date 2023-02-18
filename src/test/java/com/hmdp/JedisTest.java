package com.hmdp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;

/**
 * @author illusion
 * @date 2023/2/16 15:05
 */
@SpringBootTest
public class JedisTest {
    private Jedis jedis;

    @Test
    public void tesss() {
        String result = jedis.set("name", "张三");
        System.out.println(result);
        System.out.println(jedis.get("name"));
    }

    @BeforeEach
    public void setUp() {
        jedis = new Jedis("192.168.20.200", 6379);
        jedis.auth("123456");
    }

    @AfterEach
    public void tearDown() {
        if (jedis != null)
            jedis.close();
    }
}
