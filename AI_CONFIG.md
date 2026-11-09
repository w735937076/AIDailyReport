# AI 主备模型配置

当前主模型为 `https://api.minimax.cn/v1` 的 `MiniMax-M3.1-Flash`，备用模型为原有 DeepSeek。
主模型使用 `config.yaml` 中的 `ai.apiKey`、`ai.baseUrl` 和 `ai.model`；备用模型使用 `ai.fallback` 下的对应字段，均使用 OpenAI 兼容接口。

## 启动

主备密钥已配置在本地 `config.yaml`，该文件已加入 `.gitignore`，直接启动即可：

```powershell
mvn spring-boot:run
```

如果希望通过环境变量管理 MiniMax 密钥，可将主模型的 `ai.apiKey` 改成 `"${MINIMAX_API_KEY:}"`，并在终端或 IDE 运行配置中设置 `MINIMAX_API_KEY`。
密钥需具备所选模型的访问权限；订阅 Key 与普通开放平台 Key 使用各自的额度。
CLI 的“查看当前配置”可查看备用模型及密钥是否已配置，不显示密钥内容。
未设置备用密钥时，继续使用主模型；设置 `ai.fallback.enabled: false` 可关闭备用模型。

## 切换规则

- 提交解析、日报、周报、月报和技术文档共用同一套调用策略。
- 配置了备用模型时，主模型每次请求只尝试一次；连接失败、超时、HTTP 408/429/5xx、认证/额度错误（401/402/403），或接口响应缺少非空文本内容时立即尝试备用模型。
- HTTP 400 等请求错误直接报错，避免重复提交同一错误请求。
- 主模型连续失败 `failureThreshold` 次（默认 2），进入 `cooldownMs` 冷却（默认 120000 毫秒）。冷却期间直接调用备用模型；冷却结束后的下一次请求尝试主模型，成功后清零失败计数。
- 备用模型最多尝试 `maxRetries` 次（默认 3，包含首次），只对暂时性错误重试。重试间隔为 `retryDelayMs × 已失败次数`。
- 没有可用备用模型时，主模型按 `maxRetries` 尝试。主备均失败时抛出错误并保留失败原因。

主备模型各自支持 `connectTimeoutMs`（默认 5000）和 `readTimeoutMs`（默认 60000）。
读取超时限制等待响应数据的时间，并非整个报告生成流程的总时长上限。
所有配置项位于 `config.yaml` 的 `ai` 下，备用模型配置位于 `ai.fallback` 下。
自动切换在运行时发生；修改配置文件或环境变量后需重启程序。

## 验证

```powershell
mvn test
```

测试使用模拟接口验证切换、冷却恢复、重试、失败信息和请求协议，不消耗真实模型额度。
