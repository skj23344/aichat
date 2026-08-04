"""aichat 命令行入口:ask / chat / providers。"""

import argparse
import sys

from . import __version__
from .client import ChatClient, ChatError
from .config import build_settings
from .providers import PROVIDERS


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="aichat",
        description="aichat — 多厂商 LLM 命令行聊天客户端(OpenAI 兼容 API,零第三方依赖)",
    )
    parser.add_argument("--version", action="version", version=f"aichat {__version__}")
    parser.add_argument("--provider", help="provider 名称,如 deepseek/moonshot/qwen/ollama/custom")
    parser.add_argument("--api-key", help="API Key(优先于环境变量与配置文件)")
    parser.add_argument("--base-url", help="API Base URL(覆盖 provider 默认)")
    parser.add_argument("--model", help="模型名(覆盖 provider 默认)")
    parser.add_argument("--no-stream", action="store_true", help="关闭流式输出")

    sub = parser.add_subparsers(dest="command")

    ask = sub.add_parser("ask", help="单次提问")
    ask.add_argument("prompt", nargs="+", help="问题内容")

    sub.add_parser("chat", help="交互式多轮对话(Ctrl+C 或输入 exit 退出)")

    sub.add_parser("providers", help="列出内置 provider")

    return parser


def _stream_reply(client: ChatClient, messages: list) -> str:
    parts = []
    for piece in client.stream_chat(messages):
        print(piece, end="", flush=True)
        parts.append(piece)
    print()
    return "".join(parts)


def cmd_ask(args) -> int:
    settings = build_settings(
        args.provider, args.api_key, args.base_url, args.model, stream=not args.no_stream
    )
    client = ChatClient(settings)
    messages = [{"role": "user", "content": " ".join(args.prompt)}]
    try:
        if settings.stream:
            _stream_reply(client, messages)
        else:
            print(client.chat(messages))
    except ChatError as exc:
        print(f"\n错误: {exc}", file=sys.stderr)
        return 1
    return 0


def cmd_chat(args) -> int:
    settings = build_settings(
        args.provider, args.api_key, args.base_url, args.model, stream=not args.no_stream
    )
    client = ChatClient(settings)
    messages: list = []
    print(
        f"[aichat] provider={settings.provider.name} model={settings.effective_model()} "
        f"stream={'开' if settings.stream else '关'}"
    )
    print("[aichat] 输入 exit 或按 Ctrl+C 退出")
    try:
        while True:
            try:
                prompt = input("你> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
            if not prompt:
                continue
            if prompt.lower() in ("exit", "quit"):
                break
            messages.append({"role": "user", "content": prompt})
            print("AI> ", end="", flush=True)
            try:
                if settings.stream:
                    reply = _stream_reply(client, messages)
                else:
                    reply = client.chat(messages)
                    print(reply)
            except ChatError as exc:
                print(f"\n错误: {exc}", file=sys.stderr)
                messages.pop()
                continue
            messages.append({"role": "assistant", "content": reply})
    finally:
        print("再见!")
    return 0


def cmd_providers(args) -> int:
    print("内置 provider(OpenAI 兼容 API):")
    print()
    for provider in PROVIDERS.values():
        print(provider.display + (f"  — {provider.note}" if provider.note else ""))
    print()
    print("用法:  python -m aichat --provider deepseek ask \"你好\"")
    print("配置:  环境变量 AICHAT_PROVIDER/AICHAT_API_KEY/AICHAT_BASE_URL/AICHAT_MODEL")
    print("       或用户配置文件 ~/.aichatrc.ini([aichat] 段)")
    return 0


def main(argv=None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "ask":
            return cmd_ask(args)
        if args.command == "chat":
            return cmd_chat(args)
        if args.command == "providers":
            return cmd_providers(args)
        parser.print_help()
        return 0
    except KeyError as exc:
        # 未知 provider 等配置错误:友好提示而非 traceback
        print(f"错误: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
