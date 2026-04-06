package com.sky.service.impl;

import com.sky.service.AIService;
import com.sky.utils.QwenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AIServiceImpl implements AIService {

    private static final String MOCK_ORDER_STATS =
            "【昨日经营日报】\n" +
                    "- 总订单量：158 单\n" +
                    "- 总营业额：4,280 元\n" +
                    "- 客单价：27.1 元\n" +
                    "- 异常订单：5 单 (3 单超时，2 单退款)\n" +
                    "- 热销 Top1: 宫保鸡丁 (42 份)\n" +
                    "- 滞销 Top1: 清炒时蔬 (5 份)";

    // ========================
    // 异步方法

    @Async
    @Override
    public void runBusinessAnalysis() {
        log.info("🚀 开始异步经营分析任务 (线程名: {})", Thread.currentThread().getName());
        try {
            String prompt = "你是一位资深餐饮数据分析师。以下是昨天的经营数据：\n"
                    + MOCK_ORDER_STATS
                    + "\n\n请完成以下任务：\n1. 总结亮点。\n2. 指出潜在风险。\n3. 给出一条具体改进建议。\n请用条理清晰的格式返回。";
            String reply = QwenUtil.chat(prompt);
            log.info("✅ AI 分析完成，结果如下：\n{}", reply);
        } catch (Exception e) {
            log.error("❌ 经营分析任务执行失败", e);
        }
    }

    // ========================
    // 新增：同步方法（专门测耗时！）
    // ========================
    @Override
    public String syncRunBusinessAnalysis() {
        log.info("🚀 开始同步经营分析任务 (线程名: {})", Thread.currentThread().getName());
        try {
            String prompt = "你是一位资深餐饮数据分析师。以下是昨天的经营数据：\n"
                    + MOCK_ORDER_STATS
                    + "\n\n请完成以下任务：\n1. 总结亮点。\n2. 指出潜在风险。\n3. 给出一条具体改进建议。\n请用条理清晰的格式返回。";
            // 👇 这行是核心！同步阻塞，等待AI返回，耗时800ms
            String reply = QwenUtil.chat(prompt);
            log.info("✅ AI 分析完成，结果如下：\n{}", reply);
            return reply;
        } catch (Exception e) {
            log.error("❌ 经营分析任务执行失败", e);
            return "AI分析失败，请稍后重试";
        }
    }
}