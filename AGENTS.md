# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

This is a **Daily Report Generator** system that transforms Git commit data into structured AI-enhanced daily/weekly/monthly reports.

Core value chain: `Git行为 → 语义解析 → 结构化任务 → AI增强 → 多维聚合输出`

## Tech Stack

- **Backend**: Spring Boot + MyBatis
- **Git Operations**: JGit (Java native library)
- **AI Integration**: Minimax API with retry/rate-limiting
- **Scheduling**: Spring @Scheduled

## Architecture

```
Git Repository → Git Collection Service → Diff Parsing + AI Service → Database (structured)
                                                                      ↓
                              ┌──────────────────┬──────────────────┐
                              ↓                  ↓                  ↓
                        Daily Report      Weekly Report      Monthly Report
                         Generator         Generator           Generator
```

## Data Model

### Tables

1. **git_commit_log** - Raw commit data
   - repo_name, commit_id, author, commit_time, message, diff_text

2. **work_item** - Structured tasks (core table)
   - commit_id, work_date, type (FEATURE/BUG_FIX/REFACTOR), module, description, impact

3. **daily_report** - Generated daily reports
   - user_id, report_date, content, raw_data_json

4. **period_report** - Weekly/monthly reports
   - type (WEEK/MONTH), start_date, end_date, content

## Key Implementation Notes

1. **Always parse diff**, not just commit messages - commit messages alone are insufficient
2. **Use templates for AI output** - avoid free-form generation for consistent report style
3. **Constrain AI with JSON schemas** - validate output to prevent hallucination
4. **Handle large diffs** - split by file and limit tokens
5. **Semantic deduplication** - merge similar tasks for weekly/monthly reports

## Development Commands (TBD)

Once the project is scaffolded, standard Spring Boot commands will apply:
- Build: `mvn clean package`
- Run: `mvn spring-boot:run`
- Test: `mvn test`
