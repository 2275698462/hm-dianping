package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
        Shop shop = queryWithMutex(id);

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
}
