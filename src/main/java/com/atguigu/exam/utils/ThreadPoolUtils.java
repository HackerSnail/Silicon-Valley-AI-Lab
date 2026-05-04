package com.atguigu.exam.utils;

import org.springframework.stereotype.Component;
import java.util.concurrent.*;

/**
 * 线程池工具类
 * 用于异步任务执行
 */
@Component
public class ThreadPoolUtils {

    private final ThreadPoolExecutor executor;

    public ThreadPoolUtils() {
        // 核心线程数
        int corePoolSize = 5;
        // 最大线程数
        int maximumPoolSize = 10;
        // 空闲线程存活时间
        long keepAliveTime = 60L;
        // 任务队列
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100);
        // 线程工厂
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        // 拒绝策略
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        this.executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            workQueue,
            threadFactory,
            handler
        );
    }

    /**
     * 执行任务
     * @param task 任务
     */
    public void execute(Runnable task) {
        executor.execute(task);
    }
}