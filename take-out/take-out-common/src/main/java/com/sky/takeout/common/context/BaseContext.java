package com.sky.takeout.common.context;

/**
 * 基于 ThreadLocal 存储当前登录用户的 id
 * 
 * 多线程环境下，每个线程都有自己的 ThreadLocal 对象，互不干扰。
 * 
 * 因此，在多线程环境下，使用 ThreadLocal 存储当前登录用户的 id 是安全的。
 * 
 * 但是，在单线程环境下，使用 ThreadLocal 存储当前登录用户的 id 是浪费资源的。
 */
public final class BaseContext {

    private BaseContext() {}

    /**
     * 当前登录用户的 id
     */
    private static final ThreadLocal<Long> CURRENT_ID = new ThreadLocal<>(); 

    /**
     * 设置当前登录用户的 id
     * @param id 当前登录用户的 id
     */
    public static void setCurrentId(Long id) {
        CURRENT_ID.set(id);
    }

    /**
     * 获取当前登录用户的 id
     * @return 当前登录用户的 id
     */
    public static Long getCurrentId() {
        return CURRENT_ID.get();
    }

    /**
     * 移除当前登录用户的 id
     */
    public static void removeCurrentId() {
        CURRENT_ID.remove();
    }
    
}
