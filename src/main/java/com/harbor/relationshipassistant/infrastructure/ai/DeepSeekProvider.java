package com.harbor.relationshipassistant.infrastructure.ai;

/**
 * DeepSeek Provider（OpenAI 兼容协议，base=https://api.deepseek.com）。
 * 当前阶段唯一真正工作的 Provider；命名厂商实现便于工厂按 provider_name 路由，
 * 未来新增 Doubao/OpenAI 时再各建一个同名轻量子类，业务层仍只依赖 AIProvider。
 */
public class DeepSeekProvider extends OpenAICompatibleProvider {

    public static final String NAME = "DEEPSEEK";
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    public DeepSeekProvider(String baseUrl, String apiKey, String model) {
        super(baseUrl, apiKey, model);
    }

    @Override
    public String getProviderName() {
        return NAME;
    }
}
