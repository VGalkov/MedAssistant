package ru.medassistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class PromptService {

    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);

    // Каталог рядом с jar файлом
    private static final String EXTERNAL_PROMPTS_DIR = "prompts";

    // Путь внутри jar
    private static final String INTERNAL_PROMPTS_DIR = "prompts/";

    private final Map<String, String> promptsCache = new HashMap<>();
    private boolean useExternalPrompts = false;

    @PostConstruct
    public void init() {
        // Проверяем наличие внешнего каталога
        Path externalDir = Paths.get(EXTERNAL_PROMPTS_DIR);
        if (Files.exists(externalDir) && Files.isDirectory(externalDir)) {
            useExternalPrompts = true;
            logger.info("✅ Found external prompts directory: {}", externalDir.toAbsolutePath());
        } else {
            logger.info("ℹ️ No external prompts directory, using internal prompts from jar");
        }
    }

    /**
     * Загружает промпт по имени
     * @param name имя файла промпта (без расширения .txt)
     * @return текст промпта
     */
    public String getPrompt(String name) {
        // Проверяем кэш
        if (promptsCache.containsKey(name)) {
            logger.debug("Loading prompt '{}' from cache", name);
            return promptsCache.get(name);
        }

        String content = loadPrompt(name);
        if (content != null) {
            promptsCache.put(name, content);
        }
        return content;
    }

    private String loadPrompt(String name) {
        String fileName = name + ".txt";

        // 1. Сначала пробуем загрузить из внешнего каталога (рядом с jar)
        if (useExternalPrompts) {
            Path externalPath = Paths.get(EXTERNAL_PROMPTS_DIR, fileName);
            if (Files.exists(externalPath)) {
                try {
                    String content = Files.readString(externalPath);
                    logger.info("✅ Loaded prompt '{}' from external file: {}", name, externalPath.toAbsolutePath());
                    return content;
                } catch (IOException e) {
                    logger.warn("⚠️ Failed to read external prompt '{}': {}", name, e.getMessage());
                }
            }
        }

        // 2. Если не найдено, загружаем из jar (resources)
        String internalPath = INTERNAL_PROMPTS_DIR + fileName;
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(internalPath)) {
            if (inputStream != null) {
                String content = new String(inputStream.readAllBytes());
                logger.info("✅ Loaded prompt '{}' from internal jar: {}", name, internalPath);
                return content;
            }
        } catch (IOException e) {
            logger.warn("⚠️ Failed to read internal prompt '{}': {}", name, e.getMessage());
        }

        logger.error("❌ Prompt '{}' not found (neither external nor internal)", name);
        return null;
    }

    /**
     * Очищает кэш промптов (для перезагрузки без рестарта)
     */
    public void clearCache() {
        promptsCache.clear();
        logger.info("🔄 Prompt cache cleared");
    }

    /**
     * Проверяет, используются ли внешние промпты
     */
    public boolean isUsingExternalPrompts() {
        return useExternalPrompts;
    }

    /**
     * Возвращает путь к каталогу промптов
     */
    public String getPromptsDirectory() {
        if (useExternalPrompts) {
            return Paths.get(EXTERNAL_PROMPTS_DIR).toAbsolutePath().toString();
        } else {
            return "jar:/prompts/";
        }
    }
}