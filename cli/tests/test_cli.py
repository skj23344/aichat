import unittest

from aichat.cli import _sanitize


class SanitizeTest(unittest.TestCase):
    def test_strips_ansi_csi_sequences(self):
        self.assertEqual(_sanitize("\x1b[31mred\x1b[0m"), "red")

    def test_strips_osc_sequences(self):
        self.assertEqual(_sanitize("a\x1b]0;title\x07b"), "ab")
        self.assertEqual(_sanitize("a\x1b]8;;http://x\x1b\\link\x1b]8;;\x1b\\b"), "alinkb")

    def test_strips_carriage_returns(self):
        self.assertEqual(_sanitize("a\rb"), "ab")

    def test_keeps_normal_text_and_newlines(self):
        self.assertEqual(_sanitize("你好\n世界"), "你好\n世界")


if __name__ == "__main__":
    unittest.main()
