import unittest

from aichat.cli import _sanitize


class SanitizeTest(unittest.TestCase):
    def test_strips_ansi_csi_sequences(self):
        self.assertEqual(_sanitize("\x1b[31mred\x1b[0m"), "red")

    def test_strips_osc_sequences(self):
        self.assertEqual(_sanitize("a\x1b]0;title\x07b"), "ab")
        self.assertEqual(_sanitize("a\x1b]8;;http://x\x1b\\link\x1b]8;;\x1b\\b"), "alinkb")

    def test_strips_dcs_apc_pm_sos(self):
        self.assertEqual(_sanitize("a\x1bPdcs\x1b\\b"), "ab")
        self.assertEqual(_sanitize("a\x1b_apc\x1b\\b"), "ab")
        self.assertEqual(_sanitize("a\x1b^pm\x1b\\b"), "ab")

    def test_strips_csi_param_variants(self):
        # 参数类含 <=> 的 CSI
        self.assertEqual(_sanitize("\x1b[>c"), "")
        self.assertEqual(_sanitize("\x1b[<5m"), "")

    def test_strips_8bit_c1_controls(self):
        # 8 位 C1:CSI=\x9b, OSC=\x9d, DCS=\x90, PM=\x9e, APC=\x9f
        self.assertEqual(_sanitize("\x9b31mred"), "red")
        self.assertEqual(_sanitize("a\x9d0;title\x07b"), "ab")
        self.assertEqual(_sanitize("a\x90dcs\x9c b"), "a b")
        self.assertEqual(_sanitize("a\x9epm\x9c b"), "a b")
        self.assertEqual(_sanitize("a\x9fapc\x9c b"), "a b")

    def test_strips_bidirectional_controls(self):
        # Unicode 双向控制符(RLO/LRO/PDF 等)防 bidi 伪装
        self.assertEqual(_sanitize("\u202eevil"), "evil")
        self.assertEqual(_sanitize("a\u202db"), "ab")

    def test_strips_bel(self):
        self.assertEqual(_sanitize("a\x07b"), "ab")

    def test_strips_carriage_returns(self):
        self.assertEqual(_sanitize("a\rb"), "ab")

    def test_keeps_normal_text_and_newlines(self):
        self.assertEqual(_sanitize("你好\n世界"), "你好\n世界")


if __name__ == "__main__":
    unittest.main()
