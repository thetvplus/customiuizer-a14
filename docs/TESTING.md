# 测试

## 层

| 层 | 入口 | 用途 |
|---|---|---|
| 静态契约 | `tools/verify.py`、`tools/check-invariants.py` | SDK、作用域、API 101/102、热路径危险模式 |
| Python 工具 | `python -m unittest discover -s tools/tests -p "test_*.py"` | 工具、ROM matrix、CI 可移植性 |
| Android JVM | `testDebugUnitTest` | 行为、契约、生命周期、回归 |
| Brutal | `tools/brutal_test_runner.py` | 独立 kill：CI、catalog、fatal、observer、matrix |
| Full CI | GitHub Actions `a14-full-ci.yml` | 双 develop 构建、APK semantic diff、lintVital |

优先保留行为测试、兼容契约、备份 V2、preference、lifecycle ownership、hot-path 回归、正式 Dynamic Island、API 边界和 issue 回归。

删除测试的唯一理由：无 production subject、完全重复、或锁死错误实现细节。不得靠删测试制造绿构建。

## 实机

静态通过不等于目标 ROM 可用。实机证据按 `STATIC` / `BUILD` / `LOG` / `DEVICE` 分级。无证据不得改成熟热路径。
