package com.dailyreport.util;

public class PromptTemplate {

    private static final String DIFF_PARSE_PROMPT = """
            你是一个资深开发工程师，请分析以下 Git commit message，提取开发任务。

            要求：
            1. 输出严格 JSON 数组格式
            2. 每个任务包含：
               - type: FEATURE(新功能)/BUG_FIX(修复)/REFACTOR(重构)/DOCS(文档)/OTHER(其他)
               - module: 所属模块，如"订单模块"、"支付模块"、"用户模块"，无法判断填"其他"
               - plan: 简洁的工作计划，20字内
               - practice: 具体的做法/实现方式，50字内
            3. 如果 commit message 无有效内容，返回空数组 []
            4. 只输出 JSON，不要其他文字

            输入的 Git commit message:
            ---
            %s
            ---

            输出:
            """;

    private static final String DAILY_REPORT_PROMPT = """
            请根据以下结构化工作内容生成日报。

            今日工作 (JSON):
            %s

            用户手动填写的今日非代码类工作:
            %s

            用户填写的明日计划:
            %s

            重要约束：
            1. 每天日报量控制在3-5条，只保留有价值、有意义的工作
            2. 简单的小功能、琐碎的修改不需要体现在日报中（如 typo 修复、注释添加等）
            3. 相同或相似的工作合并为一条
            4. 优先保留：重要功能开发、bug修复、重构优化、有价值的技术改进

            日报格式如下，每部分按序号排列：

            今日工作
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1[严格按照X月X日完成格式输出]
            2、预计完成时间2[严格按照X月X日完成格式输出]
            ...

            1、完成进度1[严格按照 已完成|按计划进行 格式输出]
            2、完成进度2[严格按照 已完成|按计划进行 格式输出]
            ...

            明日计划
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1[严格按照X月X日完成格式输出]
            2、预计完成时间2[严格按照X月X日完成格式输出]
            ...

            1、按计划进行[严格按照 按计划进行 格式输出]
            2、按计划进行[严格按照 按计划进行 格式输出]
            ...

            输出格式 (严格 JSON):
            {
              "work_plans": ["计划1", "计划2"],
              "practices": ["做法1", "做法2"],
              "estimates": ["4月16日完成", "4月17日完成"],
              "progress": ["已完成", "已完成"],
              "tomorrow_plans": ["计划1", "计划2"],
              "tomorrow_practices": ["做法1", "做法2"],
              "tomorrow_estimates": ["4月18日完成", "4月19日完成"],
              "tomorrow_progress": ["按计划进行", "按计划进行"]
            }
            """;

    private static final String WEEKLY_REPORT_PROMPT = """
            请根据以下结构化工作内容生成周报。

            本周工作 (JSON):
            %s

            重要约束：
            1. 每周周报量控制在10-18条，只保留有价值的工作
            2. 简单的小功能、琐碎的修改不需要体现在周报中
            3. 相同或相似的工作必须合并为一条
            4. 优先保留：重要功能开发、bug修复、重构优化、有价值的技术改进
            5. 确保每个任务的预计完成时间是真实的，不要出现全部相同的时间

            周报格式与日报一致，四个部分：

            本周工作
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1[严格按照X月X日完成格式输出]
            2、预计完成时间2[严格按照X月X日完成格式输出]
            ...

            1、完成进度1
            2、完成进度2
            ...

            下周计划
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1[严格按照X月X日完成格式输出]
            2、预计完成时间2[严格按照X月X日完成格式输出]
            ...

            1、按计划进行[严格按照 按计划进行 格式输出]
            2、按计划进行[严格按照 按计划进行 格式输出]
            ...

            输出格式 (严格 JSON):
            {
              "work_plans": ["计划1", "计划2"],
              "practices": ["做法1", "做法2"],
              "estimates": ["4月16日完成", "4月17日完成"],
              "progress": ["完成", "进行中"],
              "next_plans": ["下周计划1", "下周计划2"],
              "next_practices": ["做法1", "做法2"],
              "next_estimates": ["4月22日完成", "4月23日完成"],
              "next_progress": ["按计划进行", "按计划进行"]
            }
            """;

    private static final String MONTHLY_REPORT_PROMPT = """
            请根据以下结构化工作内容生成月报。

            本月工作 (JSON):
            %s

            重要约束：
            1. 每月月报量控制在8-10条，只保留有价值的工作
            2. 简单的小功能、琐碎的修改不需要体现在月报中
            3. 相同或相似的工作必须合并为一条
            4. 优先保留：重要功能开发、bug修复、重构优化、有价值的技术改进

            月报格式与周报一致，四个部分：

            本月工作
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1
            2、预计完成时间2
            ...

            1，完成进度1
            2，完成进度2
            ...

            下月计划
            1、工作计划1
            2、工作计划2
            ...

            1、具体做法1
            2、具体做法2
            ...

            1、预计完成时间1
            2、预计完成时间2
            ...

            1、待开始
            2、进行中
            ...

            输出格式 (严格 JSON):
            {
              "work_plans": ["计划1", "计划2"],
              "practices": ["做法1", "做法2"],
              "estimates": ["4月16日完成", "4月17日完成"],
              "progress": ["完成", "进行中"],
              "next_plans": ["下月计划1", "下月计划2"],
              "next_practices": ["做法1", "做法2"],
              "next_estimates": ["4月28日完成", "4月29日完成"],
              "next_progress": ["待开始", "进行中"]
            }
            """;

    public static String getDiffParsePrompt(String diff) {
        return String.format(DIFF_PARSE_PROMPT, diff);
    }

    public static String getDailyReportPrompt(String workItemsJson, String tomorrowPlan) {
        return getDailyReportPrompt(workItemsJson, "", tomorrowPlan);
    }

    public static String getDailyReportPrompt(String workItemsJson, String nonCodeWork, String tomorrowPlan) {
        return String.format(DAILY_REPORT_PROMPT, workItemsJson, nonCodeWork, tomorrowPlan);
    }

    public static String getWeeklyReportPrompt(String workItemsJson) {
        return String.format(WEEKLY_REPORT_PROMPT, workItemsJson);
    }

    public static String getMonthlyReportPrompt(String workItemsJson) {
        return String.format(MONTHLY_REPORT_PROMPT, workItemsJson);
    }

    private static final String TECH_DOC_PROMPT = """
            请根据以下结构化工作内容生成技术文档。

            本周工作 (JSON):
            %s

            要求：
            1. 根据工作内容自动识别项目名称和模块名称，填入基本信息表
            2. 项目名称通常来自 git 仓库名或系统名称
            3. 模块名称根据本周主要工作内容提炼，简洁明确
            4. 所有表格字段都要填充实际内容，不要留空

            技术文档命名格式: {项目名}-{功能名}-技术文档-汪志立-【yyyy-MM-dd】.md

            文档格式如下：

            ## 基本信息
            | 项目 | 内容 |
            |------|------|
            | 项目名称 | （根据工作内容识别） |
            | 模块名称 | （根据工作内容识别） |
            | 作者 | 汪志立 |
            | 日期 | yyyy-MM-dd |

            ---

            ## 一、需求概述
            （1-2句话描述功能目标）

            ---

            ## 二、数据库设计
            ### 2.1 表结构
            | 字段 | 类型 | 说明 |
            |------|------|------|
            （根据工作内容涉及的表填充）

            ### 2.2 索引设计
            （如果有）

            ---

            ## 三、接口设计
            ### 3.1 接口列表
            | 接口 | 方法 | 路径 | 说明 |
            |------|------|------|
            （根据工作内容涉及的接口填充）

            ### 3.2 请求/响应示例
            ```json
            // 请求
            {}
            // 响应
            {}
            ```

            ---

            ## 四、架构设计
            ### 4.1 整体架构
            （简要描述层次结构）

            ### 4.2 核心流程
            （关键业务逻辑流程）

            ---

            ## 五、设计模式
            （使用了哪些设计模式及选择原因）

            ---

            ## 六、亮点设计
            （核心创新点、性能优化、可扩展性设计等）

            输出格式 (严格 JSON):
            {
              "projectName": "根据工作内容识别的项目名称",
              "moduleName": "根据工作内容识别的模块名称",
              "date": "yyyy-MM-dd",
              "requirementSummary": "需求概述",
              "tables": [{"tableName": "表名", "fields": [{"name": "字段名", "type": "类型", "desc": "说明"}], "indexes": "索引设计"}],
              "interfaces": [{"name": "接口名称", "method": "GET/POST", "path": "/api/path", "desc": "接口说明"}],
              "requestExample": "{}",
              "responseExample": "{}",
              "architecture": "整体架构描述",
              "coreFlow": "核心流程描述",
              "designPatterns": "设计模式描述",
              "highlights": "亮点设计描述"
            }
            """;

    public static String getTechDocPrompt(String workItemsJson) {
        return String.format(TECH_DOC_PROMPT, workItemsJson);
    }

    private static final String PERFORMANCE_REVIEW_PROMPT = """
            你是技术部绩效评估撰写助手。根据下方「内参证据」归纳本人当月工作，撰写「月度绩效评估表」长文内容（不是日报/月报 bullet 风格）。

            ## 岗位与周期（内参，勿在正文复述姓名）
            - 部门：%s
            - 岗位：%s
            - 评估月份：%s
            - 配置姓名（严禁写入 KPI/态度/综合评估等任何输出字段）：%s
            - 评估人（仅内参）：%s

            ## 当月工作规模统计（内参，勿在正文出现版本库术语或提交次数）
            %s

            ## 工作证据摘要（内参，勿照搬原文到输出）
            %s

            ## 写作风格参考（仅模仿语气与段落结构；禁止复制主题、人名、Git/提交类表述）
            %s

            ## 叙述立场（必须遵守）
            1. 全文用第一人称「我」自述，语气正式、连贯，像在填写本人的绩效表。
            2. 禁止出现任何真实姓名、昵称、第三人称指代（如「该员工」「本人某某」「他/她」指自己）。
            3. 禁止在输出中出现：Git、提交、commit、哈希、分支、仓库、push、merge、工作项抓取、diff 等版本库或采集术语；
               也不得写「根据提交记录」「于某日提交」「提交时间为…」等句式。只允许描述做了什么事、解决了什么问题、带来什么结果。
            4. 内参里的统计数字可用于把握工作量，但综合评估须用业务语言概括（如参与方向、交付重点、协同面），不要罗列提交数或仓库名。

            ## 输出要求
            1. 只输出一个 JSON 对象，无其它文字。
            2. 字段（四个字段全部遵守上述叙述立场）：
               - kpiD5：岗位 KPI 完成情况（对应 Excel D5）。按业务域/项目主题拆分，每个主题格式严格为：
                 、1、主题标题
                 （空行）
                 引导句（承上启下，第一人称）
                 （空行）
                 1、… 2、… 编号条目（长句，具体工作与影响，第一人称）
                 （空行）
                 本主题小结
                 （空行）
                 --------------------------------------------------
                 下一主题…
                 主题须从本月证据归纳，不得照搬风格参考里的项目名。
               - auxiliaryD12：其它辅助性绩效（D12），长段落，第一人称；可写跨项目协作、稳定性、工程规范等。
               - attitudeD15：工作态度（D15），长段落，第一人称；可分责任心与推进、团队协作、主动学习、执行质量等。
               - overallB38：综合评估意见（B38），第一人称总结本月工作重心、质量与贡献；不得引用版本库或数据采集说法，不得出现人名。
            3. 全文用中文，偏正式书面语，篇幅充实；禁止编造未在证据中出现的重大上线或事故。
            """;

    public static String getPerformanceReviewPrompt(String department, String role, String assessee, String assessor,
                                                    String yearMonthLabel, String statsJson, String evidence,
                                                    String styleExcerpt) {
        String style = styleExcerpt != null && !styleExcerpt.isBlank() ? styleExcerpt : "（无历史样例，按公司绩效表常见长文格式撰写）";
        return String.format(PERFORMANCE_REVIEW_PROMPT,
                department, role, yearMonthLabel, assessee, assessor, statsJson, evidence, style);
    }

    private static final String PERFORMANCE_INTERVIEW_PROMPT = """
            你是技术部「绩效面谈记录表」撰写助手。根据下方内参证据，用第一人称「我」填写面谈表答案区（对应 Excel F3/F4/F5/F6/F8；F7 由部门负责人填写，不要输出）。

            ## 岗位与周期（内参，勿在答案字段复述姓名）
            - 部门：%s
            - 岗位：%s
            - 面谈所属月份：%s
            - 配置姓名（严禁写入任何答案字段）：%s

            ## 当月工作规模统计（内参，勿在答案中出现版本库术语）
            %s

            ## 工作证据摘要（内参线索，勿照搬原文）
            %s

            ## 同月绩效评估长文摘要（若有，仅供把握重点；答案须简短，禁止照抄长文）
            %s

            ## 写作要求（必须遵守）
            1. 第一人称「我」；每题约 150～250 个汉字；至少结合 1～2 个本月真实工作案例，具体但简洁。
            2. 禁止人名、他/她/该员工；配置姓名不得出现在输出。
            3. 禁止 Git、提交、commit、分支、仓库、merge、diff、「根据提交记录」等表述。
            4. 不得编造证据中不存在的工作；F9 若无靠谱建议则写「无」。
            5. 语气口语化、诚恳，比绩效评估长文短得多，不要分主题长文或大量分隔线。

            ## 输出
            只输出一个 JSON 对象，字段：
            - answerF3：上一阶段做得较好的方面（对应问题 1）
            - answerF4：成功经验总结（问题 2）
            - answerF5：需改善的方面（问题 3）
            - answerF6：遇到的困难及解决方式（问题 4）
            - answerF8：接下来工作目标与计划（问题 5）
            - answerF9：对部门及公司机制的疑问；没有则填「无」
            """;

    public static String getPerformanceInterviewPrompt(String department, String role, String assessee,
                                                       String yearMonthLabel, String statsJson, String evidence,
                                                       String performanceExcerpt) {
        String perf = performanceExcerpt != null && !performanceExcerpt.isBlank()
                ? performanceExcerpt
                : "（本月暂无已生成的绩效评估长文）";
        return String.format(PERFORMANCE_INTERVIEW_PROMPT,
                department, role, yearMonthLabel, assessee, statsJson, evidence, perf);
    }
}
