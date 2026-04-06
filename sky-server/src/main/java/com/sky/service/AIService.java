package com.sky.service;

public interface AIService {

    /**
     * 异步执行经营分析任务
     */
    void runBusinessAnalysis();

    // 👇 新增：专门用于测试的同步方法（返回String，不带Async）
    String syncRunBusinessAnalysis();
}
