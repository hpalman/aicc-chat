package aicc.bot;

import java.util.function.Consumer;

import aicc.bot.dto.ChatBotRequest;

public interface ChatBot {

    void ask(ChatBotRequest requests, Consumer<String> onChunk, Runnable onComplete);
}
