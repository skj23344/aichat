import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from aichat.config import ENV_API_KEY, build_settings, load_config_file

MISSING = Path("nonexistent-rc.ini")


class ConfigTest(unittest.TestCase):
    def test_load_config_file(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "rc.ini"
            p.write_text("[aichat]\nprovider = deepseek\napi_key = sk-file\n", encoding="utf-8")
            cfg = load_config_file(p)
            self.assertEqual(cfg["provider"], "deepseek")
            self.assertEqual(cfg["api_key"], "sk-file")

    def test_load_missing_file_returns_empty(self):
        self.assertEqual(load_config_file(MISSING), {})

    def test_env_overrides_file(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "rc.ini"
            p.write_text(
                "[aichat]\nprovider = deepseek\napi_key = sk-file\nmodel = deepseek-chat\n",
                encoding="utf-8",
            )
            with mock.patch.dict(os.environ, {ENV_API_KEY: "sk-env"}, clear=False):
                s = build_settings(config_path=p)
            self.assertEqual(s.api_key, "sk-env")
            self.assertEqual(s.provider.name, "deepseek")

    def test_provider_specific_env_key_fallback(self):
        with mock.patch.dict(os.environ, {"DEEPSEEK_API_KEY": "sk-ds"}, clear=False):
            s = build_settings(provider_name="deepseek", config_path=MISSING)
        self.assertEqual(s.api_key, "sk-ds")

    def test_defaults_to_openai(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            s = build_settings(config_path=MISSING)
        self.assertEqual(s.provider.name, "openai")
        self.assertEqual(s.effective_model(), "gpt-4o-mini")

    def test_explicit_args_win_over_env(self):
        with mock.patch.dict(os.environ, {ENV_API_KEY: "sk-env"}, clear=False):
            s = build_settings(api_key="sk-arg", provider_name="qwen", config_path=MISSING)
        self.assertEqual(s.api_key, "sk-arg")
        self.assertEqual(
            s.effective_base_url(), "https://dashscope.aliyuncs.com/compatible-mode/v1"
        )

    def test_stream_flag_passthrough(self):
        s = build_settings(stream=False, config_path=MISSING)
        self.assertFalse(s.stream)

    def test_unknown_provider_raises_friendly_key_error(self):
        with self.assertRaises(KeyError) as ctx:
            build_settings(provider_name="no-such-provider", config_path=MISSING)
        self.assertIn("no-such-provider", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
