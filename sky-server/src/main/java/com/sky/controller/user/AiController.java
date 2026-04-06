package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.AIService;
import com.sky.utils.QwenUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.cache.annotation.Cacheable;

import javax.annotation.PostConstruct;

/**
 * AI 智能服务控制器
 */
@RestController
@RequestMapping("/user/ai")
@Api(tags = "AI 智能服务")
@Slf4j
public class AiController {


    @Autowired
    private AIService aiService; // 注入我们刚才写的异步服务

    // === 模拟数据区 (Mock Data) ===
    // 注意：把 Mock 数据提出来作为常量，方便异步 Service 调用
    public static final String MOCK_MENU =
            "1. 宫保鸡丁 (28 元，辣，鸡肉，热销)\n" +
                    "2. 麻婆豆腐 (18 元，辣，豆制品，实惠)\n" +
                    "3. 清炒时蔬 (15 元，清淡，蔬菜，健康)\n" +
                    "4. 水煮鱼 (68 元，特辣，鱼肉，招牌)\n" +
                    "5. 番茄鸡蛋盖饭 (22 元，微甜，家常，快速)\n" +
                    "6. 红烧肉 (35 元，甜咸，猪肉，经典)";

    public static final String MOCK_ORDER_STATS =
            "【昨日经营日报】\n" +
                    "- 总订单量：158 单\n" +
                    "- 总营业额：4,280 元\n" +
                    "- 客单价：27.1 元\n" +
                    "- 异常订单：5 单 (3 单超时，2 单退款)\n" +
                    "- 热销 Top1: 宫保鸡丁 (42 份)\n" +
                    "- 滞销 Top1: 清炒时蔬 (5 份)";

    @Value("${sky.ai.qwen.api-key:}")
    private String configApiKey;

    @PostConstruct
    public void init() {
        if (configApiKey != null && !configApiKey.isEmpty()) {
            QwenUtil.setApiKey(configApiKey);
            log.info("✅ AI 初始化成功：Key 已加载");
        } else {
            log.error("❌ AI 初始化失败：未找到 sky.ai.api-key");
        }
    }

    // ==========================================================
    // 👇 这是唯一保留的 analysis 方法：负责接收请求并触发后台任务
    // ==========================================================
    @GetMapping("/analysis")
    @ApiOperation("AI 经营日报分析 (异步版)")
    public Result<String> analysis() {
        log.info("⏰ 用户请求到达：触发异步经营分析");

        // 1. 触发异步任务（注意：这里不写 .get()，不等待结果）
        aiService.runBusinessAnalysis();

        // 2. 立刻返回响应给用户
        // 提示：因为是异步，这里无法直接拿到 AI 的结果，所以只能返回“已启动”
        return Result.success("AI分析已启动，请稍后查看结果（当前主线程已释放）");
    }

    //同步接口
    @GetMapping("/analysis/sync")
    @ApiOperation("AI 经营日报分析 (同步版)")
    public Result<String> syncAnalysis() {
        log.info("⏰ 同步请求到达：等待AI分析完成");

        // 1. 同步调用，会阻塞直到AI返回结果（这里必须用有返回值的方法）
        String result = aiService.syncRunBusinessAnalysis(); // 调用我们新增的同步方法

        // 2. 返回AI真实结果
        return Result.success(result);
    }
    // ==========================================================
    // 👇 这是另一个接口：智能推荐 (保持不变)
    // ==========================================================
    @GetMapping("/recommend")
    @ApiOperation("AI 智能点餐推荐")
    @Cacheable(value = "aiRecommend", key = "#preference")
    public Result<String> recommend(@RequestParam String preference) {
        log.info("AI 推荐请求 - 用户偏好：{}", preference);

        if (preference == null || preference.trim().isEmpty()) {
            preference = "随便吃点";
        }

        String prompt = "你是一个专业的外卖点餐助手。以下是今日可用菜单：\n" + MOCK_MENU +
                "\n\n用户需求：" + preference +
                "\n\n请根据用户需求，从菜单中精准推荐 1-2 道菜品，并简短说明推荐理由。直接返回推荐内容。";

        String reply = QwenUtil.chat(prompt);
        return Result.success(reply);
    }

    // ==========================================================
    // 👇 页面跳转 (保持不变)
    // ==========================================================
    @GetMapping("/page")
    @ApiOperation("跳转到点餐页面")
    public String toOrderPage() {
        return "ai-order";
    }
}