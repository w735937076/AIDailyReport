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
}
