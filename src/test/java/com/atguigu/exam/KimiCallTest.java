package com.atguigu.exam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@SpringBootTest
public class KimiCallTest {

    private String apiKey = "sk-BWLueii0HDyBeNWaZp4ChIynhjmB97G9IFKnkF2oZsou29lL";

    private String kimiBaseUrl = "https://api.moonshot.cn/v1";

    // 初始化WebClient
    private final WebClient webClient = WebClient.builder()
            .baseUrl(kimiBaseUrl+ "/chat/completions")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        	
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();

    @Test
    public void testChat(){

        // 1. 构建消息列表（包含system和user角色）
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统消息
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是 Kimi，由 Moonshot AI 提供的人工智能助手，你更擅长中文和英文的对话。你会为用户提供安全，有帮助，准确的回答。同时，你会拒绝一切涉及恐怖主义，种族歧视，黄色暴力等问题的回答。Moonshot AI 为专有名词，不可翻译成其他语言。");
        messages.add(systemMsg);

        // 用户消息
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "我是赵伟风，请问 1+1等于多少？");
        messages.add(userMsg);

        // 2. 构建请求体（包含模型、消息和参数）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "moonshot-v1-32k"); // 指定Kimi模型
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.6); // 温度参数

        Mono<String> mono = webClient.post().bodyValue(requestBody).retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    // 解析响应JSON，提取回答内容
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = null;
                    try {
                        jsonNode = objectMapper.readTree(response);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return jsonNode
                            .get("choices")
                            .get(0)
                            .get("message")
                            .get("content")
                            .asText();
                });

        String block = mono.block();
        System.out.println("block = " + block);
    }
}