import http.client
import io
import json
import unittest
from unittest import mock
from urllib.error import HTTPError

from aichat.client import MAX_RESPONSE_BYTES, ChatClient, ChatError
from aichat.config import Settings
from aichat.providers import PROVIDERS


def make_settings(**kw) -> Settings:
    base = dict(provider=PROVIDERS["deepseek"], api_key="test-key", stream=True)
    base.update(kw)
    return Settings(**base)


class FakeResponse(io.BytesIO):
    """模拟 urllib HTTPResponse:可按行迭代。"""

    def __init__(self, body: bytes):
        super().__init__(body)
        self.status = 200


class RaisingIterResponse:
    """迭代时抛指定异常(模拟 socket 超时/连接重置/IncompleteRead)。"""

    def __init__(self, exc: BaseException):
        self._exc = exc

    def __iter__(self):
        raise self._exc

    def close(self):
        pass


class ChatClientTest(unittest.TestCase):
    def test_stream_chat_joins_deltas_and_stops_at_done(self):
        sse = (
            b'data: {"choices":[{"delta":{"role":"assistant"}}]}\n\n'
            b'data: {"choices":[{"delta":{"content":"Hello"}}]}\n\n'
            b'data: {"choices":[{"delta":{"content":"World"}}]}\n\n'
            b'data: [DONE]\n\n'
        )
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        reply = "".join(client.stream_chat([{"role": "user", "content": "hi"}]))
        self.assertEqual(reply, "HelloWorld")

    def test_stream_skips_non_content_lines(self):
        sse = (
            b': keep-alive comment\n\n'
            b'data: {"choices":[{"delta":{"content":"A"}}]}\n\n'
            b'data: {"choices":[{"delta":{}}]}\n\n'
            b'data: {"choices":[{"delta":{"content":"B"}}]}\n\n'
            b'data: [DONE]\n\n'
        )
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        self.assertEqual("".join(client.stream_chat([])), "AB")

    def test_stream_top_level_non_dict_is_ignored(self):
        # 顶层非 dict(如 data: [1,2])应静默忽略,不抛 AttributeError
        sse = (
            b'data: [1, 2]\n\n'
            b'data: {"choices":[{"delta":{"content":"A"}}]}\n\n'
            b'data: [DONE]\n\n'
        )
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        self.assertEqual("".join(client.stream_chat([])), "A")

    def test_stream_error_event_raises_chat_error(self):
        sse = (
            b'data: {"choices":[{"delta":{"content":"A"}}]}\n\n'
            b'data: {"error":{"message":"boom"}}\n\n'
        )
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        with self.assertRaises(ChatError) as ctx:
            "".join(client.stream_chat([]))
        self.assertIn("boom", str(ctx.exception))

    def test_stream_multiline_data_event(self):
        # SSE 规范:单个事件可拆成多行 data:,行间以 \n 连接成完整 JSON
        sse = (
            b'data: {"choices":[{"delta":{"content":"Hel'
            b'lo"}}]}\n\n'
            b'data: [DONE]\n\n'
        )
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        self.assertEqual("".join(client.stream_chat([])), "Hello")

    def test_custom_provider_without_base_url_raises(self):
        opener = mock.Mock()
        client = ChatClient(
            make_settings(provider=PROVIDERS["custom"], base_url="", model="m"), opener=opener
        )
        with self.assertRaises(ChatError) as ctx:
            list(client.stream_chat([]))
        self.assertIn("Base URL", str(ctx.exception))

    def test_custom_provider_without_model_raises(self):
        opener = mock.Mock()
        client = ChatClient(
            make_settings(provider=PROVIDERS["custom"], base_url="http://x", model=""), opener=opener
        )
        with self.assertRaises(ChatError) as ctx:
            list(client.stream_chat([]))
        self.assertIn("模型", str(ctx.exception))

    def test_chat_null_message_raises_chat_error(self):
        payload = {"choices": [{"message": None}]}
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(json.dumps(payload).encode("utf-8"))
        client = ChatClient(make_settings(stream=False), opener=opener)
        with self.assertRaises(ChatError):
            client.chat([])

    def test_chat_null_content_raises_chat_error(self):
        payload = {"choices": [{"message": {"content": None}}]}
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(json.dumps(payload).encode("utf-8"))
        client = ChatClient(make_settings(stream=False), opener=opener)
        with self.assertRaises(ChatError):
            client.chat([])

    def test_chat_non_string_content_raises_chat_error(self):
        payload = {"choices": [{"message": {"content": {"oops": 1}}}]}
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(json.dumps(payload).encode("utf-8"))
        client = ChatClient(make_settings(stream=False), opener=opener)
        with self.assertRaises(ChatError):
            client.chat([])

    def test_request_payload_and_auth_header(self):
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(b"data: [DONE]\n\n")
        client = ChatClient(make_settings(), opener=opener)
        list(client.stream_chat([{"role": "user", "content": "hi"}]))
        req = opener.open.call_args[0][0]
        self.assertEqual(req.get_header("Authorization"), "Bearer test-key")
        # urllib 3.14 将 header key capitalize 为 "Content-type",dict 精确匹配
        self.assertEqual(req.get_header("Content-type"), "application/json")
        body = json.loads(req.data.decode("utf-8"))
        self.assertEqual(body["model"], "deepseek-chat")
        self.assertTrue(body["stream"])
        self.assertEqual(body["messages"], [{"role": "user", "content": "hi"}])
        self.assertTrue(req.full_url.endswith("/chat/completions"))

    def test_stream_network_error_raises_chat_error(self):
        opener = mock.Mock()
        opener.open.return_value = RaisingIterResponse(ConnectionResetError("conn reset"))
        client = ChatClient(make_settings(), opener=opener)
        with self.assertRaises(ChatError) as ctx:
            list(client.stream_chat([]))
        self.assertIn("流式读取失败", str(ctx.exception))

    def test_stream_json_recursion_error_is_ignored(self):
        sse = b'data: {"choices":[{"delta":{"content":"X"}}]}\n\n'
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        with mock.patch("aichat.client.json.loads", side_effect=RecursionError("deep")):
            self.assertEqual("".join(client.stream_chat([])), "")

    def test_chat_json_recursion_error_raises_chat_error(self):
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(b"{}")
        client = ChatClient(make_settings(stream=False), opener=opener)
        with mock.patch("aichat.client.json.loads", side_effect=RecursionError("deep")):
            with self.assertRaises(ChatError):
                client.chat([])

    def test_stream_incomplete_read_raises_chat_error(self):
        opener = mock.Mock()
        opener.open.return_value = RaisingIterResponse(
            http.client.IncompleteRead(b"partial")
        )
        client = ChatClient(make_settings(), opener=opener)
        with self.assertRaises(ChatError) as ctx:
            list(client.stream_chat([]))
        self.assertIn("流式读取失败", str(ctx.exception))

    def test_stream_buffer_limit_raises_chat_error(self):
        # 模拟累积 data 缓冲超限(10 MiB 太大,直接构造超限数据不可行;
        # 用多条 8MiB 行会占用大量内存,故验证单行超限路径)
        sse = b"data: " + b"x" * (MAX_RESPONSE_BYTES + 1) + b"\n\n"
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(sse)
        client = ChatClient(make_settings(), opener=opener)
        with self.assertRaises(ChatError) as ctx:
            list(client.stream_chat([]))
        self.assertIn("MiB 上限", str(ctx.exception))

    def test_chat_non_stream_returns_content(self):
        payload = {"choices": [{"message": {"content": "Full reply"}}]}
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(json.dumps(payload).encode("utf-8"))
        client = ChatClient(make_settings(stream=False), opener=opener)
        self.assertEqual(client.chat([{"role": "user", "content": "hi"}]), "Full reply")

    def test_http_error_raises_chat_error_with_message(self):
        body = b'{"error":{"message":"Invalid API key"}}'
        exc = HTTPError("http://x/chat/completions", 401, "Unauthorized", {}, io.BytesIO(body))
        opener = mock.Mock()
        opener.open.side_effect = exc
        client = ChatClient(make_settings(), opener=opener)
        try:
            with self.assertRaises(ChatError) as ctx:
                client.chat([])
            self.assertIn("401", str(ctx.exception))
            self.assertIn("Invalid API key", str(ctx.exception))
        finally:
            exc.close()

    def test_missing_api_key_is_allowed(self):
        opener = mock.Mock()
        opener.open.return_value = FakeResponse(b"data: [DONE]\n\n")
        client = ChatClient(make_settings(api_key=None), opener=opener)
        list(client.stream_chat([]))
        req = opener.open.call_args[0][0]
        self.assertIsNone(req.get_header("Authorization"))


if __name__ == "__main__":
    unittest.main()
