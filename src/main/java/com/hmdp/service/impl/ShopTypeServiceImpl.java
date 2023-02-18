package com.hmdp.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String cacheType = stringRedisTemplate.opsForValue().get("cache:type");
        List<ShopType> shopTypes = JSONUtil.toList(cacheType, ShopType.class); //json转为list
        if (cacheType != null) {
            return Result.ok(shopTypes);
        }
        shopTypes = query().orderByAsc("sort").list();
        if (shopTypes == null) {
            return Result.fail("不存在哦");
        }

        String jsonStr = JSONUtil.toJsonStr(shopTypes); //list转json
        stringRedisTemplate.opsForValue().set("cache:type", jsonStr);
        return Result.ok(shopTypes);
    }


}
