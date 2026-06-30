package domain.rag

/**
 * Контрольный набор вопросов для проверки качества RAG.
 *
 * Здесь специально собраны вопросы по локальной базе проекта:
 * без индекса модель может только угадывать, а не знать эти детали.
 */
object RagEvaluationQuestions {

    val cases: List<RagEvaluationCase> = listOf(
        RagEvaluationCase(
            id = "q01_task_state_artifacts",
            question = "Почему TaskState не должен хранить тексты планов и результатов?",
            expectedAnswer = "В ответе должно быть сказано, что TaskState хранит только компактное машинное состояние: stage, currentStep и expectedAction. Планы, execution output и validation summary должны храниться отдельно в TaskArtifact, чтобы состояние не разрасталось и его было проще восстанавливать.",
            expectedSources = listOf(
                "project/invariants.md",
                "project/architecture.md",
                "project/stateful-agent-lifecycle.md",
            ),
        ),
        RagEvaluationCase(
            id = "q02_approval_transition",
            question = "Какой переход запрещён без пользовательского approval?",
            expectedAnswer = "В ответе должен быть указан переход PLANNING -> EXECUTION. Агент не должен начинать выполнение только потому, что модель сгенерировала план; сначала пользователь должен подтвердить план.",
            expectedSources = listOf(
                "project/stateful-agent-lifecycle.md",
                "reports/day-15-state-machine.md",
            ),
        ),
        RagEvaluationCase(
            id = "q03_transition_validator",
            question = "Зачем в проекте нужен TaskTransitionValidator?",
            expectedAnswer = "В ответе должно быть объяснено, что TaskTransitionValidator проверяет допустимость переходов между стадиями и не даёт LLM произвольно переводить задачу в EXECUTION или DONE.",
            expectedSources = listOf(
                "project/architecture.md",
                "reports/day-15-state-machine.md",
            ),
        ),
        RagEvaluationCase(
            id = "q04_pipeline_parser_problem",
            question = "Почему PipelineRequestParser в Day 19 был плохим решением?",
            expectedAnswer = "В ответе должно быть сказано, что PipelineRequestParser был hardcoded if/else или regex-подходом, который выбирал pipeline по ключевым словам, а не по descriptions tools. Это не настоящий tool-using agent и плохо масштабируется.",
            expectedSources = listOf(
                "reports/day-19-tool-composition.md",
            ),
        ),
        RagEvaluationCase(
            id = "q05_day19_vs_day20",
            question = "Чем Day 19 отличается от Day 20?",
            expectedAnswer = "В ответе должно быть сравнение: Day 19 — composition tools внутри одного MCP-сервера, где агент выбирает toolName; Day 20 — multi-MCP orchestration, где агент выбирает serverName + toolName и маршрутизирует вызовы между несколькими MCP-серверами.",
            expectedSources = listOf(
                "reports/day-19-tool-composition.md",
                "reports/day-20-multi-mcp-orchestration.md",
            ),
        ),
        RagEvaluationCase(
            id = "q06_day20_servers",
            question = "Какие два MCP-сервера использовались в Day 20?",
            expectedAnswer = "В ответе должны быть названы github-data и workspace-files. github-data — собственный HTTP MCP-сервер с GitHub/text tools, workspace-files — Filesystem MCP через stdio для работы с локальной workspace-директорией.",
            expectedSources = listOf(
                "reports/day-20-multi-mcp-orchestration.md",
            ),
        ),
        RagEvaluationCase(
            id = "q07_wrong_save_tool",
            question = "Почему save_note_to_file был неправильным tool для сохранения отчёта в workspace?",
            expectedAnswer = "В ответе должно быть сказано, что github-data.save_note_to_file сохранял note во внутреннюю директорию mcp-tools-server storage/notes, а не в workspace пользователя. Для workspace нужно было использовать workspace-files.write_file.",
            expectedSources = listOf(
                "reports/day-20-multi-mcp-orchestration.md",
            ),
        ),
        RagEvaluationCase(
            id = "q08_filtered_github_tools",
            question = "Какие tools остались у github-data после фильтрации registry в Day 20?",
            expectedAnswer = "В ответе должны быть названы get_recent_commits, search_recent_commits и summarize_text. Конфликтующий save_note_to_file был исключён из registry для этого сценария.",
            expectedSources = listOf(
                "reports/day-20-multi-mcp-orchestration.md",
            ),
        ),
        RagEvaluationCase(
            id = "q09_chunk_metadata",
            question = "Какие metadata сохраняются у каждого chunk при индексации документов?",
            expectedAnswer = "В ответе должны быть перечислены metadata вроде chunk_id, source, title, section, strategy, start_char/startChar, end_char/endChar, text и embedding.",
            expectedSources = listOf(
                "corpus_manifest.md",
                "project/README.md",
            ),
        ),
        RagEvaluationCase(
            id = "q10_chunking_strategies",
            question = "Чем fixed-size chunking отличается от structure-aware chunking?",
            expectedAnswer = "В ответе должно быть сказано, что fixed-size режет текст на примерно одинаковые куски с overlap, но может разрывать смысл. Structure-aware учитывает структуру: markdown-заголовки, Kotlin-объявления и абзацы, поэтому chunks обычно смысловее, но размеры менее равномерные.",
            expectedSources = listOf(
                "corpus_manifest.md",
            ),
        ),
    )
}

data class RagEvaluationCase(
    val id: String,
    val question: String,
    val expectedAnswer: String,
    val expectedSources: List<String>,
)

data class RagEvaluationItem(
    val case: RagEvaluationCase,
    val comparison: RagComparisonResult,
)