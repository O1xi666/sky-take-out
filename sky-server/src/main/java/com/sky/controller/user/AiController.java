package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.utils.QwenUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 智能服务控制器
 * 注意：本模块采用 Mock 数据模式，不依赖 Dish/Order 等未完成的业务模块，确保独立运行。
 */
@RestController
@RequestMapping("/user/ai")
@Api(tags = "AI 智能服务")
@Slf4j
public class AiController {

    // === 模拟数据区 (Mock Data) ===
    // 面试话术：实际生产中这里会调用 DishService 获取动态数据，演示环境使用静态配置以突出 AI 逻辑
    private static final String MOCK_MENU =
            "1. 宫保鸡丁 (28元, 辣, 鸡肉, 热销)\n" +
                    "2. 麻婆豆腐 (18元, 辣, 豆制品, 实惠)\n" +
                    "3. 清炒时蔬 (15元, 清淡, 蔬菜, 健康)\n" +
                    "4. 水煮鱼 (68元, 特辣, 鱼肉, 招牌)\n" +
                    "5. 番茄鸡蛋盖饭 (22元, 微甜, 家常, 快速)\n" +
                    "6. 红烧肉 (35元, 甜咸, 猪肉, 经典)";

    private static final String MOCK_ORDER_STATS =
            "【昨日经营日报】\n" +
                    "- 总订单量：158 单\n" +
                    "- 总营业额：4,280 元\n" +
                    "- 客单价：27.1 元\n" +
                    "- 异常订单：5 单 (3 单超时，2 单退款)\n" +
                    "- 热销 Top1: 宫保鸡丁 (42 份)\n" +
                    "- 滞销 Top1: 清炒时蔬 (5 份)";

    @GetMapping("/recommend")
    @ApiOperation("AI 智能点餐推荐")
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

    @GetMapping("/analysis")
    @ApiOperation("AI 经营日报分析")
    public Result<String> analysis() {
        log.info("AI 经营分析请求");

        String prompt = "你是一位资深餐饮数据分析师。以下是昨天的经营数据：\n" + MOCK_ORDER_STATS +
                "\n\n请完成以下任务：\n1. 总结亮点。\n2. 指出潜在风险。\n3. 给出一条具体改进建议。\n请用条理清晰的格式返回。";

        String reply = QwenUtil.chat(prompt);
        String htmlReply = reply.replace("\n", "<br>");
        return Result.success(htmlReply);
    }
}
