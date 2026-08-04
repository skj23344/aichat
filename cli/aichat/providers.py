"""内置 LLM provider 注册表(OpenAI 兼容 API)。"""

from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class Provider:
    name: str
    base_url: str
    default_model: str
    env_key: Optional[str] = None
    note: str = ""

    @property
    def display(self) -> str:
        env = f", 环境变量 {self.env_key}" if self.env_key else ""
        return f"{self.name:<10} {self.base_url:<46} 默认模型 {self.default_model}{env}"


PROVIDERS = {
    "openai": Provider(
        "openai",
        "https://api.openai.com/v1",
        "gpt-4o-mini",
        "OPENAI_API_KEY",
    ),
    "deepseek": Provider(
        "deepseek",
        "https://api.deepseek.com/v1",
        "deepseek-chat",
        "DEEPSEEK_API_KEY",
    ),
    "moonshot": Provider(
        "moonshot",
        "https://api.moonshot.cn/v1",
        "moonshot-v1-8k",
        "MOONSHOT_API_KEY",
    ),
    "qwen": Provider(
        "qwen",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "qwen-plus",
        "DASHSCOPE_API_KEY",
    ),
    "ollama": Provider(
        "ollama",
        "http://localhost:11434/v1",
        "llama3",
        None,
        note="本地 Ollama,无需 API Key",
    ),
    "custom": Provider(
        "custom",
        "",
        "",
        None,
        note="通过 --base-url / --api-key / --model 指定",
    ),
}


def get_provider(name: str) -> Provider:
    if name not in PROVIDERS:
        raise KeyError(f"未知 provider: {name},可选: {', '.join(PROVIDERS)}")
    return PROVIDERS[name]
