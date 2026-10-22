package com.example.model

data class PromptTemplate(
    val id: String,
    val title: String,
    val category: String,
    val prompt: String,
    val iconName: String
)

object PromptLibrary {
    val templates: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "t_code_refactor",
            title = "Code Refactor & Review",
            category = "Development",
            prompt = "Review the following code for readability, performance bottlenecks, and security vulnerabilities. Propose a refactored version with explanations:\n\n```\n\n```",
            iconName = "Code"
        ),
        PromptTemplate(
            id = "t_code_explain",
            title = "Explain Complex Code",
            category = "Development",
            prompt = "Break down how the following snippet operates line by line, including edge cases and execution complexity:\n\n```\n\n```",
            iconName = "Terminal"
        ),
        PromptTemplate(
            id = "t_email_reply",
            title = "Executive Email Drafter",
            category = "Productivity",
            prompt = "Draft a professional, courteous, and assertive email response addressing the following key points: \n1. Acknowledge and appreciate the input\n2. Clarify our deliverable timeline\n3. Outline the next concrete steps",
            iconName = "Email"
        ),
        PromptTemplate(
            id = "t_summarize",
            title = "Executive 5-Bullet Summary",
            category = "Productivity",
            prompt = "Distill the following article/document into an executive summary containing:\n- The Core Problem\n- 5 Essential Key Takeaways\n- Immediate Actionable Recommendations\n\nText:\n",
            iconName = "Article"
        ),
        PromptTemplate(
            id = "t_brainstorm",
            title = "Lateral Brainstorming",
            category = "Creativity",
            prompt = "Brainstorm 10 innovative, outside-the-box concepts for: ",
            iconName = "Lightbulb"
        ),
        PromptTemplate(
            id = "t_simplify",
            title = "Explain Like I'm Five",
            category = "Learning",
            prompt = "Explain the fundamental principles of the following concept using everyday real-world analogies: ",
            iconName = "School"
        ),
        PromptTemplate(
            id = "t_vision_analyze",
            title = "Analyze Attached Image",
            category = "Vision",
            prompt = "Analyze this image in detail. Identify the primary subjects, transcribe any visible text, describe visual hierarchy, and explain context.",
            iconName = "Image"
        ),
        PromptTemplate(
            id = "t_interview_prep",
            title = "Mock Interview Practice",
            category = "Career",
            prompt = "Act as an interviewer for a Senior Software Engineer position. Ask me one challenging technical/behavioral question at a time and evaluate my response before proceeding.",
            iconName = "Psychology"
        )
    )
}
