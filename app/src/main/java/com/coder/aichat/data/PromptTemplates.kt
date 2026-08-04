package com.coder.aichat.data

/** Prompt 模板库 — 内置一批高质量指令模板，{content} 为内容占位符 */
object PromptTemplates {

    data class Template(val name: String, val prompt: String)

    val all = listOf(
        Template("代码解释", "请解释以下代码的作用，逐行或分块说明：\n\n{content}"),
        Template("代码生成", "请根据以下需求编写代码，并简要说明实现思路：\n\n{content}"),
        Template("代码优化", "请优化以下代码的性能和可读性，并说明改动点：\n\n{content}"),
        Template("中文翻译", "请将以下内容翻译成中文，保持原意与语气：\n\n{content}"),
        Template("英文翻译", "请将以下内容翻译成地道的英文：\n\n{content}"),
        Template("内容总结", "请用简洁的语言总结以下内容的要点：\n\n{content}"),
        Template("文字润色", "请润色以下文字，使其更通顺、专业、有文采：\n\n{content}"),
        Template("改写重述", "请用不同的表达方式改写以下内容：\n\n{content}"),
        Template("头脑风暴", "请针对以下主题，给出 5 个富有创意的想法：\n\n{content}"),
        Template("小红书文案", "请根据以下内容写一篇小红书风格文案，加入 emoji 和话题标签：\n\n{content}"),
        Template("周报生成", "请根据以下工作内容生成一份结构清晰的周报：\n\n{content}"),
        Template("内容扩展", "请基于以下内容进行详细扩展，补充背景和细节：\n\n{content}"),
        Template("通俗解释", "请用通俗易懂的语言解释以下概念，适合小白：\n\n{content}"),
        Template("利弊分析", "请分析以下主题的优缺点，并给出建议：\n\n{content}"),
    )
}
