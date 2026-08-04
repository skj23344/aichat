"""配置加载:显式参数 > 环境变量 > 用户配置文件(~/.aichatrc.ini)> provider 默认值。"""

import configparser
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .providers import PROVIDERS, Provider

CONFIG_PATH = Path.home() / ".aichatrc.ini"
ENV_PROVIDER = "AICHAT_PROVIDER"
ENV_API_KEY = "AICHAT_API_KEY"
ENV_BASE_URL = "AICHAT_BASE_URL"
ENV_MODEL = "AICHAT_MODEL"


@dataclass
class Settings:
    provider: Provider
    api_key: Optional[str] = None
    base_url: str = ""
    model: str = ""
    stream: bool = True

    def effective_base_url(self) -> str:
        return self.base_url or self.provider.base_url

    def effective_model(self) -> str:
        return self.model or self.provider.default_model


def load_config_file(path: Path = CONFIG_PATH) -> dict:
    """读取 INI 配置文件中 [aichat] 段,不存在的文件返回空 dict。"""
    data = {}
    if not path.exists():
        return data
    parser = configparser.ConfigParser()
    parser.read(path, encoding="utf-8")
    if parser.has_section("aichat"):
        for key in ("provider", "api_key", "base_url", "model"):
            if parser.has_option("aichat", key):
                data[key] = parser.get("aichat", key).strip()
    return data


def build_settings(
    provider_name: Optional[str] = None,
    api_key: Optional[str] = None,
    base_url: Optional[str] = None,
    model: Optional[str] = None,
    stream: bool = True,
    config_path: Path = CONFIG_PATH,
) -> Settings:
    """按优先级合并配置:显式参数 > 环境变量 > 配置文件 > provider 默认。"""
    file_cfg = load_config_file(config_path)

    name = provider_name or os.environ.get(ENV_PROVIDER) or file_cfg.get("provider") or "openai"
    provider = PROVIDERS[name]

    key = (
        api_key
        or os.environ.get(ENV_API_KEY)
        or (os.environ.get(provider.env_key) if provider.env_key else None)
        or file_cfg.get("api_key")
        or None
    )
    url = base_url or os.environ.get(ENV_BASE_URL) or file_cfg.get("base_url") or ""
    mdl = model or os.environ.get(ENV_MODEL) or file_cfg.get("model") or ""

    return Settings(provider=provider, api_key=key, base_url=url, model=mdl, stream=stream)
