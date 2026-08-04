# aichat CLI

aichat 的多厂商 LLM 命令行客户端(本仓库 `cli/` 子目录):支持 OpenAI 兼容 API 的
多家服务商(OpenAI / DeepSeek / Moonshot(Kimi)/ 通义千问 / Ollama / 自定义),
流式输出,**零第三方依赖**(仅 Python 标准库)。

## 运行

```bash
cd cli
python -m aichat --help
```

## 用法

单次提问:

```bash
python -m aichat --provider deepseek ask "你好,介绍一下你自己"
```

交互式多轮对话:

```bash
python -m aichat --provider moonshot chat
```

列出内置 provider:

```bash
python -m aichat providers
```

常用选项(优先级:命令行参数 > 环境变量 > 配置文件 > provider 默认):

| 选项 | 说明 |
| --- | --- |
| `--provider` | provider 名称:openai / deepseek / moonshot / qwen / ollama / custom |
| `--api-key` | API Key |
| `--base-url` | API Base URL(覆盖 provider 默认) |
| `--model` | 模型名(覆盖 provider 默认) |
| `--no-stream` | 关闭流式输出 |

## 配置

### 环境变量

| 变量 | 说明 |
| --- | --- |
| `AICHAT_PROVIDER` | 默认 provider |
| `AICHAT_API_KEY` | API Key(通用) |
| `AICHAT_BASE_URL` | API Base URL |
| `AICHAT_MODEL` | 模型名 |

各 provider 也有专用环境变量,如 `DEEPSEEK_API_KEY`、`OPENAI_API_KEY`、
`MOONSHOT_API_KEY`、`DASHSCOPE_API_KEY`。

### 配置文件 `~/.aichatrc.ini`

```ini
[aichat]
provider = deepseek
api_key = sk-xxxx
model = deepseek-chat
```

## 测试

```bash
# 在仓库根目录运行
python -m unittest discover -s cli/tests -t cli -v
```

## 目录结构

```
cli/
├── aichat/
│   ├── __init__.py      # 版本
│   ├── __main__.py      # python -m aichat 入口
│   ├── cli.py           # 命令行(ask / chat / providers)
│   ├── client.py        # OpenAI 兼容流式客户端(SSE)
│   ├── config.py        # 配置合并(参数 > 环境变量 > 文件 > 默认)
│   └── providers.py     # 内置 provider 注册表
├── tests/               # 单元测试(unittest + mock)
├── pyproject.toml
└── README.md
```
