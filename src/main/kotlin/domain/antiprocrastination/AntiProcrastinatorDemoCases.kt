package domain.antiprocrastination

object AntiProcrastinatorDemoCases {

    val cases: List<AntiProcrastinatorRequest> = listOf(
        AntiProcrastinatorRequest(
            task = "Обновить README по проекту после добавления RAG-чата и локальной LLM",
            blocker = "Кажется, что надо переписать всё идеально, поэтому я третий день не начинаю",
            availableMinutes = 25,
            energyLevel = "low",
        ),
        AntiProcrastinatorRequest(
            task = "Разобрать накопившиеся бытовые дела: документы, стирку, посуду и список покупок",
            blocker = "Всего слишком много, я смотрю на список и просто зависаю",
            availableMinutes = 20,
            energyLevel = "low",
        ),
        AntiProcrastinatorRequest(
            task = "Начать подготовку к сложной теме, которую я давно откладываю",
            blocker = "Я не понимаю, с чего начать, и боюсь, что всё равно ничего не запомню",
            availableMinutes = 30,
            energyLevel = "normal",
        ),
    )
}