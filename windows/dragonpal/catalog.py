"""Provider presets plus searchable model lists, with paid models flagged."""

PROVIDERS = [
    "Custom / manual",
    "OpenAI",
    "OpenRouter",
    "Groq",
    "Together AI",
    "Google Gemini",
    "DeepSeek",
    "Hugging Face",
    "Ollama (local)",
]


class Preset:
    def __init__(self, base_url, default_chat, default_vision, chat_models):
        self.base_url = base_url
        self.default_chat = default_chat
        self.default_vision = default_vision
        self.chat_models = list(chat_models)


def _p(base_url, default_chat, default_vision, *models):
    return Preset(base_url, default_chat, default_vision, models)


PRESETS = [
    _p("", "", ""),
    _p("https://api.openai.com/v1", "gpt-4o-mini", "gpt-4o-mini",
       "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
       "gpt-4-turbo", "gpt-4", "o1", "o1-mini", "o3", "o3-mini", "o4-mini"),
    _p("https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openai/gpt-4o-mini",
       "openai/gpt-4o-mini", "openai/gpt-4o", "openai/gpt-4.1",
       "anthropic/claude-3.7-sonnet", "anthropic/claude-3.5-sonnet", "anthropic/claude-3.5-haiku", "anthropic/claude-3-opus",
       "google/gemini-2.5-pro", "google/gemini-2.0-flash-001",
       "meta-llama/llama-3.3-70b-instruct", "meta-llama/llama-3.1-405b-instruct",
       "deepseek/deepseek-chat", "deepseek/deepseek-r1",
       "qwen/qwen-2.5-72b-instruct", "mistralai/mistral-large-2411"),
    _p("https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "llama-3.2-90b-vision-preview",
       "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768",
       "gemma2-9b-it", "qwen-2.5-32b", "qwen-2.5-coder-32b",
       "llama-3.2-90b-vision-preview", "llama-3.2-11b-vision-preview"),
    _p("https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo",
       "meta-llama/Llama-3.3-70B-Instruct-Turbo", "meta-llama/Llama-3.1-405B-Instruct-Turbo", "meta-llama/Llama-3.1-8B-Instruct-Turbo",
       "mistralai/Mixtral-8x7B-Instruct-v0.1", "Qwen/Qwen2.5-72B-Instruct-Turbo", "deepseek-ai/DeepSeek-V3"),
    _p("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash", "gemini-2.0-flash",
       "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-1.5-pro"),
    _p("https://api.deepseek.com", "deepseek-v4-flash", "",
       "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner"),
    _p("https://router.huggingface.co/v1", "meta-llama/Llama-3.3-70B-Instruct", "meta-llama/Llama-3.2-11B-Vision-Instruct",
       "meta-llama/Llama-3.3-70B-Instruct", "meta-llama/Llama-3.1-8B-Instruct",
       "Qwen/Qwen2.5-72B-Instruct", "deepseek-ai/DeepSeek-V3",
       "mistralai/Mistral-7B-Instruct-v0.3", "google/gemma-2-9b-it",
       "microsoft/Phi-3.5-mini-instruct", "NousResearch/Hermes-3-Llama-3.1-8B"),
    _p("http://localhost:11434/v1", "llama3.2", "llama3.2-vision",
       "llama3.2", "llama3.1", "qwen2.5", "mistral", "gemma2", "phi3"),
]


def preset(index):
    if index < 0 or index >= len(PRESETS):
        return PRESETS[0]
    return PRESETS[index]


def detect(url):
    u = url or ""
    if "openrouter" in u:
        return 2
    if "groq" in u:
        return 3
    if "together" in u:
        return 4
    if "generativelanguage" in u:
        return 5
    if "deepseek" in u:
        return 6
    if "huggingface" in u:
        return 7
    if "localhost" in u or "127.0.0.1" in u:
        return 8
    if "api.openai.com" in u:
        return 1
    return 0


def chat_models(provider_index):
    models = preset(provider_index).chat_models
    return models if models else _all_chat_models()


VISION_MODELS = [
    "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini",
    "openai/gpt-4o-mini", "openai/gpt-4o",
    "gemini-2.0-flash", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash", "gemini-1.5-pro",
    "google/gemini-2.0-flash-001", "google/gemini-2.5-pro",
    "llama-3.2-90b-vision-preview", "llama-3.2-11b-vision-preview",
    "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo", "meta-llama/Llama-3.2-11B-Vision-Instruct-Turbo",
    "meta-llama/Llama-3.2-11B-Vision-Instruct", "Qwen/Qwen2.5-VL-7B-Instruct",
    "llama3.2-vision", "llava", "qwen2.5vl",
]

PAID = {
    "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
    "gpt-4-turbo", "gpt-4", "o1", "o1-mini", "o3", "o3-mini", "o4-mini",
    "openai/gpt-4o-mini", "openai/gpt-4o", "openai/gpt-4.1",
    "anthropic/claude-3.7-sonnet", "anthropic/claude-3.5-sonnet",
    "anthropic/claude-3.5-haiku", "anthropic/claude-3-opus",
    "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat", "deepseek-reasoner",
}


def is_paid(model_id):
    return model_id in PAID


BASE_URLS = [
    "https://api.openai.com/v1",
    "https://openrouter.ai/api/v1",
    "https://api.groq.com/openai/v1",
    "https://api.together.xyz/v1",
    "https://generativelanguage.googleapis.com/v1beta/openai",
    "https://api.deepseek.com",
    "https://router.huggingface.co/v1",
    "http://localhost:11434/v1",
]


_ALL = None


def _all_chat_models():
    global _ALL
    if _ALL is None:
        seen = []
        for p in PRESETS:
            for m in p.chat_models:
                if m not in seen:
                    seen.append(m)
        _ALL = seen
    return _ALL
