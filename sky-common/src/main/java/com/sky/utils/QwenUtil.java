package com.sky.utils;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问 AI 工具类
 * 放置在 sky-common 模块中，方便全局调用
 */
public class QwenUtil {

    private static final String API_KEY = "sk-89c9ca4712eb4226843a79dac130204e";

    private static final String URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    public static String chat(String prompt) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "qwen-plus");

        JSONObject parameters = new JSONObject();
        parameters.put("result_format", "message");
        requestBody.put("parameters", parameters);

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.put(userMsg);

        JSONObject input = new JSONObject();
        input.put("messages", messages);
        requestBody.put("input", input);

        RequestBody bodyContent = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(bodyContent)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "AI 服务调用失败: " + response.message();
            }

            String respBody = response.body().string();
            JSONObject json = new JSONObject(respBody);

            if (json.has("output")) {
                JSONObject output = json.getJSONObject("output");
                if (output.has("choices")) {
                    JSONArray choices = output.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        if (choice.has("message")) {
                            JSONObject message = choice.getJSONObject("message");
                            if (message.has("content")) {
                                return message.getString("content");
                            }
                        }
                    }
                }
            }
            return "未获取到有效回复";

        } catch (IOException e) {
            e.printStackTrace();
            return "网络异常，请稍后重试：" + e.getMessage();
        }
    }
}
