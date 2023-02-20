

-- 获取key的唯一value
local value = redis.call('get', KEYS[1])

-- 比较线程标识和锁中的value是否一致
if(ARGV[1] == value) then
    -- 释放锁
    return redis.call('del', KEYS[1])
end

-- 不一致返回0，因为删除成功是返回1
return 0