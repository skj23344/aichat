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
        base = self.settings.effective_base_url()
        if not base:
            raise ChatError("未配置 API Base URL(custom provider 需 --base-url 或 AICHAT_BASE_URL)")
        return base.rstrip("/") + "/chat/completions"

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
    def _emit_sse_event(data: str) -> Iterator[str]:
        """解析一个完整 SSE data 块,产出文本增量;流中含 error 时抛 ChatError。"""
        if not data or data == "[DONE]":
            return
        try:
            chunk = json.loads(data)
        except json.JSONDecodeError:
            return
        if not isinstance(chunk, dict):
            return
        error = chunk.get("error")
        if error:
            message = error.get("message", str(error)) if isinstance(error, dict) else str(error)
            raise ChatError(f"流式响应错误: {message}")
        choices = chunk.get("choices") or []
        if not choices:
            return
        delta = choices[0].get("delta") or {}
        content = delta.get("content")
        if content:
            yield content

    def stream_chat(self, messages: List[dict]) -> Iterator[str]:
        """流式对话,逐段产出文本增量;支持跨行 data 块,流中出错时抛 ChatError。"""
        resp = self._open(messages, stream=True)
        data_buf: List[str] = []
        try:
            for raw in resp:
                for line in raw.decode("utf-8", "replace").splitlines():
                    line = line.strip()
                    if not line:
                        # 空行:SSE 事件结束,解析累积的 data 块
                        if data_buf:
                            yield from self._emit_sse_event("\n".join(data_buf))
                            data_buf = []
                        continue
                    if line.startswith("data:"):
                        data_buf.append(line[len("data:"):].strip())
                    # 其他 SSE 字段(comment/event/id)忽略
            if data_buf:
                yield from self._emit_sse_event("\n".join(data_buf))
        finally:
            resp.close()

    def chat(self, messages: List[dict]) -> str:
        """非流式对话,返回完整回复文本。"""
        resp = self._open(messages, stream=False)
        try:
            try:
                data = json.loads(resp.read().decode("utf-8"))
            except json.JSONDecodeError as exc:
                raise ChatError("响应不是合法 JSON") from exc
            try:
                message = data["choices"][0]["message"]
                if message is None:
                    raise ChatError(f"响应缺少 choices[0].message: {data}")
                content = message["content"]
                if content is None:
                    raise ChatError("模型返回了空内容(choices[0].message.content 为 null)")
                return content
            except (KeyError, IndexError, TypeError) as exc:
                raise ChatError(f"响应缺少 choices[0].message.content: {data}") from exc
        finally:
            resp.close()
