package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author illusion
 * @date 2023/2/18 15:49
 */
@SuppressWarnings("ALl")
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     *
     * @param key
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，
     * 并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds((unit.toSeconds(time))));

        redisData.setData(value);
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPreifx
     * @param id
     * @param type
     * @param dbFunction 查询数据库，1参数，1返回值
     * @param time
     * @param unit
     * @param <R>
     * @param <V>
     * @return
     */
    public <R, V> R queryWithPenetrate(
            String keyPreifx, V id, Class<R> type, Function<V, R> dbFunction, Long time, TimeUnit unit
    ) {

        String key = keyPreifx + id;
        //从redis查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断缓存是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在，返回
            return JSONUtil.toBean(json, type);
        }

        //判断缓存是否为 ""
        if (json != null)
            //是 "",直接返回
            return null;

        //不存在，根据id查询数据库
        R r = dbFunction.apply(id);

        //判断数据库是否存在
        if (r == null)
            //不存在，返回空对象
            stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES);

        //存在，写入redis
        this.set(key, r, time, unit);
        return r;
    }

    //封装查询逻辑过期的缓存对象，方便二次检查
    private <R, T> R selectLogicCache(String keyPreifx, T id, Class<R> type) {
        return null;
        //TODO
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     *
     * @param keyPreifx
     * @param id
     * @param type
     * @param dbFunction
     * @param time
     * @param unit
     * @param <R>
     * @param <T>
     * @return
     */
    public <R, T> R queryWithLogicExpire(
            String keyPreifx, T id, Class<R> type, Function<T, R> dbFunction, Long time, TimeUnit unit
    ) {
        String key = keyPreifx + id;
        //从redis查询
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断是否为 ""
        if (StrUtil.isBlank(json))
            //为 ""，直接返回
            return null;

        //缓存有数据，要把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now()))
            //未过期，返回
            return r;

        //过期，尝试获取互斥锁
        String lockKey = "lock:shop:" + id;
        boolean isLock = tryLock(lockKey);

        //判断是否获取锁成功
        if (!isLock) {
            //获取失败，返回旧数据
            return r;
        }

        //获取成功，再次判断缓存是否过期，TODO
        if (expireTime.isAfter(LocalDateTime.now()))
            //未过期，返回
            return r;

        //确认拿到的是过期的数据，开启独立线程
        pool.submit(() -> {
            try {
                //查询数据库
                R r1 = dbFunction.apply(id);
                //写入缓存
                this.setWithLogicExpire(key, r1, time, unit);
            } finally {
                if (isLock)
                    unlock(lockKey);
            }
        });
        return r;
    }

    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //线程池
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

}
