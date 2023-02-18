package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@SuppressWarnings("all")
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryById(Long id) {
        //解决缓存穿透--缓存空对象
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicExpire(id);
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete("cache:shop:" + id);
        return null;
    }

    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); //不直接返回flag，避免拆箱空指针
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    public Shop queryWithMutex(Long id) {
        //1.从redis查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断缓存是否为空对象
        if (shopJson != null) {
            return null;
        }

        //实现缓存重建
        //获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;

        boolean isLock = tryLock(lockKey);
        try {
            //判断获取锁是否成功
            if (!isLock) {
                //失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id); //递归实现循环重试
            }

            //成功，再次检查缓存是否存在
            if (StrUtil.isNotBlank(shopJson)) {
                //3.存在，直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            //缓存不存在，则去数据库查询并写入缓存
            shop = getById(id);
            log.error("数据库查询一次");
            Thread.sleep(200);//模拟查询数据库延时
            if (shop == null) {
                //数据库不存在，返回空对象
                stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", 2, TimeUnit.MINUTES);
                return null;
            }

            stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁,得先获取锁成功才有释放资格
            if (isLock)
                unLock(lockKey);
        }

        return shop;
    }

    //线程池
    private static final ExecutorService pool = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicExpire(Long id) {
        String key = "cache:shop:" + id;
        //判断缓存是否为空
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(cacheJson)) {
            //为空直接返回
            return null;
        }
        //把cacheJson反序列化为对象
        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
        //先强转为JSONObject
        JSONObject data = (JSONObject) redisData.getData();
        //再转为对象
        Shop shop = JSONUtil.toBean(data, Shop.class);  //TODO 把以上封装为一个通用方法，在方便做二次检查，也就是下面的TODO

        //判断逻辑时间是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，返回店铺信息
            return shop;
        }

        //过期，尝试获取互斥锁
        String lockKey = "lock:shop:" + id;
        boolean flag = tryLock(lockKey);

        //获取锁失败，返回旧数据
        if (!flag) {
            return shop;
        }

        //获取锁成功，再次检查缓存是否更新 TODO 这里检查的有问题，要从redis中重新获取数据，而不是使用旧数据，先这样把，有时间再来改
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，释放锁并返回店铺信息
            unLock(lockKey);
            return shop;
        }

        //开启独立线程，返回过期数据
        pool.submit(() -> {
            try {
                //更新逻辑过期时间
                this.savaShop2Redis(id, 20L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unLock(lockKey);
            }
        });
        //获取锁成功，也返回旧数据
        return shop;
    }

    public Shop queryWithPassThrough(Long id) {
        //1.从redis查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断缓存是否为空对象
        if (shopJson != null) {
            return null;
        }

        //4.不存在，根据id查询数据库
        Shop shop = getById(id);
        //5.不存在，返回错误
        if (shop == null) {
            stringRedisTemplate.opsForValue().set("cache:shop:" + id, "", 2, TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(shop), 30L, TimeUnit.MINUTES);
        return shop;
    }

    public void savaShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = this.getById(id);
        Thread.sleep(200); //模拟查询数据库延迟
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set("cache:shop:" + id, JSONUtil.toJsonStr(redisData));
    }
}
