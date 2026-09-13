package com.example.linehaul.automation.ai;

import com.example.linehaul.util.ParsedQuestion;
import com.example.linehaul.util.QuestionParser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * The AI half of the chat assistant.
 *
 * <p>Gemini answers from live data by calling the read-only tools in {@link LinehaulDataTools}; it
 * never sees a fixed list of canned replies. Questions the built-in lookup engine already answers
 * better - greetings, "help", anything empty - are sent straight back to it, which keeps the free-tier
 * quota for the questions that really need a model.</p>
 */
@Service
public class AiChatService {

    private final AiAdvisor advisor;
    private final LinehaulDataTools dataTools;

    public AiChatService(AiAdvisor advisor, LinehaulDataTools dataTools) {
        this.advisor = advisor;
        this.dataTools = dataTools;
    }

    public boolean isEnabled() {
        return advisor.isEnabled();
    }

    public String modelName() {
        return advisor.modelName();
    }

    /** @return the model's answer, or empty when the rule based engine should answer instead. */
    public Optional<String> answer(String question, String warehouseId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            ParsedQuestion parsed = QuestionParser.parse(question);
            if (parsed.text().isEmpty() || parsed.focus() == ParsedQuestion.Focus.GREETING
                    || parsed.focus() == ParsedQuestion.Focus.HELP) {
                return Optional.empty();
            }
        } catch (Exception notUnderstandable) {
            return Optional.empty();
        }

        GeminiClient.ToolRunner runner = (name, arguments) ->
                dataTools.run(name, arguments == null ? Map.of() : arguments);
        return advisor.chat(question, warehouseId, dataTools.declarations(), runner);
    }
}
