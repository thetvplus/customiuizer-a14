# TYPE-任务名称

- Platform: A14
- Status: Backlog | Active | Blocked | Verify | Done
- Priority: P0 | P1 | P2 | P3
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: optional
- Cross-repo task: optional

## 目标

用用户可见结果描述本任务。不要写“审计某模块”作为最终目标，除非任务本身只需要报告。

## 当前问题

说明实际症状、触发条件、影响范围和已有证据。

## 允许修改

列出允许修改的模块、进程、资源和测试范围。

## 必须保持

列出不可回归的行为、Hook 时序、参数语义、兼容基线和安全边界。

## 实现要求

给出足够明确的技术约束，但不把实现拆成多个 Review/Implement 阶段。

## 非目标

明确本任务不处理的相邻问题，防止顺手扩大范围。

## 验收标准

- [ ] 用户可见结果达到目标
- [ ] 旧行为未出现未授权变化
- [ ] 相关自动测试通过
- [ ] 完整本地门禁通过
- [ ] 需要时 APK 构建成功
- [ ] 未实机验证内容已明确分级
- [ ] 最终 diff 已审查
- [ ] 工作区没有未解释改动

## 验证

```powershell
# 在此填写本任务的针对性命令
```

## 构建产物

仅任务要求 APK 时填写路径、大小、签名类型和 SHA-256。

## 完成记录

- Base SHA:
- Final SHA:
- Commits:
- Behavior changed:
- Verification:
- Device evidence:
- Known limits:
