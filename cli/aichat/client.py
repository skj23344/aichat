"""OpenAI 兼容 Chat Completions 客户端(仅标准库,支持流式 SSE)。"""

import json
import urllib.error
import urllib.request
from typing import Iterator, List, Optional

from .config import Settings


class ChatError(RuntimeError):
    """LLM 请求失败(HTTP 错误 / 网络错误 / 响应格式错误)。"""


def _extract_error(body: bytes) -> str:
    try:
        data = json.loads(body)
        message = data.get("error", {}).get("message")
        if message:
            return message
    except Exception:
        pass
    return body.decode("utf-8", "replace")


class ChatClient:
    """OpenAI 兼容 chat/completions 客户端。"""

    def __init__(
        self,
        settings: Settings,
        opener: Optional[urllib.request.OpenerDirector] = None,
        timeout: float = 60.0,
    ):
        self.settings = settings
        self.opener = opener or urllib.request.build_opener()
        self.timeout = timeout

    def _headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.settings.api_key:
            headers["Authorization"] = f"Bearer {self.settings.api_key}"
        return headers

    def _payload(self, messages: List[dict], stream: bool) -> dict:
        return {
            "model": self.settings.effective_model(),
            "messages": messages,
            "stream": stream,
        }

    def _url(self) -> str:
        return self.settings.effective_base_url().rstrip("/") + "/chat/completions"

    def _request(self, messages: List[dict], stream: bool) -> urllib.request.Request:
        body = json.dumps(self._payload(messages, stream)).encode("utf-8")
        return urllib.request.Request(self._url(), data=body, headers=self._headers(), method="POST")

    def _open(self, messages: List[dict], stream: bool):
        try:
            return self.opener.open(self._request(messages, stream), timeout=self.timeout)
        except urllib.error.HTTPError as exc:
            raise ChatError(f"HTTP {exc.code}: {_extract_error(exc.read())}") from exc
        except urllib.error.URLError as exc:
            raise ChatError(f"网络错误: {exc.reason}") from exc

    @staticmethod
    def _parse_sse_line(line: str) -> Optional[str]:
        """解析一行 SSE(data: ...),返回文本增量;无内容返回 None。"""
        line = line.strip()
        if not line.startswith("data:"):
            return None
        data = line[len("data:"):].strip()
        if not data or data == "[DONE]":
            return None
        try:
            chunk = json.loads(data)
        except json.JSONDecodeError:
            return None
        choices = chunk.get("choices") or []
        if not choices:
            return None
        delta = choices[0].get("delta") or {}
        return delta.get("content")

    def stream_chat(self, messages: List[dict]) -> Iterator[str]:
        """流式对话,逐段产出文本增量。"""
        resp = self._open(messages, stream=True)
        for raw in resp:
            for line in raw.decode("utf-8", "replace").splitlines():
                text = self._parse_sse_line(line)
                if text:
                    yield text

    def chat(self, messages: List[dict]) -> str:
        """非流式对话,返回完整回复文本。"""
        resp = self._open(messages, stream=False)
        try:
            data = json.loads(resp.read().decode("utf-8"))
        except json.JSONDecodeError as exc:
            raise ChatError("响应不是合法 JSON") from exc
        try:
            return data["choices"][0]["message"]["content"]
        except (KeyError, IndexError) as exc:
            raise ChatError(f"响应缺少 choices[0].message.content: {data}") from exc
