package com.dragonpal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Provider presets plus searchable model lists, with paid models flagged. */
public final class ModelCatalog {

    public static final String[] PROVIDERS = {
            "Custom / manual",
            "OpenAI",
            "OpenRouter",
            "Groq",
            "Together AI",
            "Google Gemini",
            "DeepSeek",
            "Hugging Face",
            "Ollama (local)"
    };

    public static final class Preset {
        public final String baseUrl;
        public final String defaultChat;
        public final String defaultVision;
        public final List<String> chatModels;
        Preset(String baseUrl, String defaultChat, String defaultVision, String... chatModels) {
            this.baseUrl = baseUrl;
            this.defaultChat = defaultChat;
            this.defaultVision = defaultVision;
            this.chatModels = Arrays.asList(chatModels);
        }
    }

    private static final Preset[] PRESETS = {
        new Preset("", "", ""),
        new Preset("https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o-mini",
            "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4-turbo", "gpt-4", "o1", "o1-mini", "o3", "o3-mini", "o4-mini"),
        new Preset("https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openai/gpt-4o-mini",
            "openai/gpt-4o-mini", "openai/gpt-4o", "openai/gpt-4.1",
            "anthropic/claude-3.7-sonnet", "anthropic/claude-3.5-sonnet", "anthropic/claude-3.5-haiku", "anthropic/claude-3-opus",
            "google/gemini-2.5-pro", "google/gemini-2.0-flash-001",
            "meta-llama/llama-3.3-70b-instruct", "meta-llama/llama-3.1-405b-instruct",
            "deepseek/deepseek-chat", "deepseek/deepseek-r1",
            "qwen/qwen-2.5-72b-instruct", "mistralai/mistral-large-2411"),
        new Preset("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "llama-3.2-90b-vision-preview",
            "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768",
            "gemma2-9b-it", "qwen-2.5-32b", "qwen-2.5-coder-32b",
            "llama-3.2-90b-vision-preview", "llama-3.2-11b-vision-preview"),
        new Preset("https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo",
            "meta-llama/Llama-3.3-70B-Instruct-Turbo", "meta-llama/Llama-3.1-405B-Instruct-Turbo", "meta-llama/Llama-3.1-8B-Instruct-Turbo",
            "mistralai/Mixtral-8x7B-Instruct-v0.1", "Qwen/Qwen2.5-72B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
        new Preset("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", "gemini-2.0-flash",
            "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-1.5-pro"),
        new Preset("https://api.deepseek.com", "deepseek-v4-flash", "",
            "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"),
        new Preset("https://router.huggingface.co/v1", "meta-llama/Llama-3.3-70B-Instruct", "meta-llama/Llama-3.2-11B-Vision-Instruct",
            "meta-llama/Llama-3.3-70B-Instruct", "meta-llama/Llama-3.1-8B-Instruct",
            "Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3",
            "mistralai/Mistral-7B-Instruct-v0.3", "google/gemma-2-9b-it",
            "microsoft/Phi-3.5-mini-instruct", "NousResearch/Hermes-3-Llama-3.1-8B"),
        new Preset("http://localhost:11434/v1", "llama3.2", "llama3.2-vision",
            "llama3.2", "llama3.1", "qwen2.5", "mistral", "gemma2", "phi3"),
    };

    public static Preset preset(int index) {
        if (index < 0 || index >= PRESETS.length) return PRESETS[0];
        return PRESETS[index];
    }

    public static int detect(String url) {
        String u = url == null ? "" : url;
        if (u.contains("openrouter")) return 2;
        if (u.contains("groq")) return 3;
        if (u.contains("together")) return 4;
        if (u.contains("generativelanguage")) return 5;
        if (u.contains("deepseek")) return 6;
        if (u.contains("huggingface")) return 7;
        if (u.contains("localhost") || u.contains("127.0.0.1")) return 8;
        if (u.contains("api.openai.com")) return 1;
        return 0;
    }

    public static List<String> chatModels(int providerIndex) {
        List<String> list = preset(providerIndex).chatModels;
        return list.isEmpty() ? ALL_CHAT_MODELS : list;
    }

    /** Vision-capable models (provider-agnostic). Includes DeepSeek's open-weight vision models. */
    public static final String[] VISION_MODELS = {
        "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini",
        "openai/gpt-4o-mini", "openai/gpt-4o",
        "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-1.5-pro",
        "google/gemini-2.0-flash-001", "google/gemini-2.5-pro",
        "llama-3.2-90b-vision-preview", "llama-3.2-11b-vision-preview",
        "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo", "meta-llama/Llama-3.2-11B-Vision-Instruct-Turbo",
        "meta-llama/Llama-3.2-11B-Vision-Instruct", "Qwen/Qwen2.5-VL-7B-Instruct",
        "llama3.2-vision", "llava", "qwen2.5vl"
    };

    /** Models that cost money (no free tier) on the endpoint they are listed under. */
    private static final Set<String> PAID = new HashSet<>(Arrays.asList(
            // OpenAI
            "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4-turbo", "gpt-4", "o1", "o1-mini", "o3", "o3-mini", "o4-mini",
            // OpenRouter: OpenAI + Anthropic
            "openai/gpt-4o-mini", "openai/gpt-4o", "openai/gpt-4.1",
            "anthropic/claude-3.7-sonnet", "anthropic/claude-3.5-sonnet",
            "anthropic/claude-3.5-haiku", "anthropic/claude-3-opus",
            // DeepSeek hosted API
            "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"
    ));

    public static boolean isPaid(String modelId) {
        return modelId != null && PAID.contains(modelId);
    }

    /** Base URLs offered in the vision base-URL dropdown. */
    public static final String[] BASE_URLS = {
        "https://api.openai.com/v1",
        "https://openrouter.ai/api/v1",
        "https://api.groq.com/openai/v1",
        "https://api.together.xyz/v1",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://api.deepseek.com",
        "https://router.huggingface.co/v1",
        "http://localhost:11434/v1"
    };

    private static final List<String> ALL_CHAT_MODELS = buildAll();

    private static List<String> buildAll() {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (Preset p : PRESETS) s.addAll(p.chatModels);
        return new ArrayList<>(s);
    }
}
