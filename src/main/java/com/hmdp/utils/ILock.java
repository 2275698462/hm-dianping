package com.hmdp.utils;

/**
 * @author illusion
 * @date 2023/2/19 19:28
 */
public interface ILock {

    /**
     * 尝试获取锁，采用非阻塞式模式，只获取一次锁
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    //释放锁
    void unlock();
}
