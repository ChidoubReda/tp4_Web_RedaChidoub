package ma.emsi.chidoub.tp4_web_redachidoub.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15.BgeSmallEnV15EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.content.retriever.WebSearchContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client centralisé pour Gemini + RAG (docs locaux + Tavily Web).
 */
@ApplicationScoped
public class LlmClient {

    private ChatMemory chatMemory;
    private Assistant assistant;
    private String systemRole;

    public LlmClient() {

        configureLogger(); // Test 2 - logs détaillés

        // --- ChatModel Gemini ---
        String geminiKey = System.getenv("GEMINI_API_KEY");
        if (geminiKey == null || geminiKey.isBlank()) {
            throw new IllegalStateException(
                    "Erreur : la variable d'environnement GEMINI_API_KEY est absente."
            );
        }

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiKey)
                .modelName("gemini-2.0-flash")
                .temperature(0.8)
                .logRequestsAndResponses(true) // logs requêtes/réponses
                .build();

        // --- PHASE 1 : ingestion des documents pour le RAG ---

        EmbeddingModel embeddingModel = new BgeSmallEnV15EmbeddingModel();

        EmbeddingStore<TextSegment> storeRag = new InMemoryEmbeddingStore<>();
        EmbeddingStore<TextSegment> storeAutre = new InMemoryEmbeddingStore<>();

        // Ces fichiers doivent être dans src/main/resources
        ingestResource("/rag.pdf", storeRag, embeddingModel);
        ingestResource("/autre.txt", storeAutre, embeddingModel); // ou /autre.pdf si tu préfères

        ContentRetriever ragRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeRag)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        ContentRetriever autreRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeAutre)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();

        // --- Tavily Web Search (Test 5) ---

        String tavilyKey = System.getenv("TAVILY_API_KEY");
        ContentRetriever webRetriever = null;

        if (tavilyKey != null && !tavilyKey.isBlank()) {
            WebSearchEngine tavily = TavilyWebSearchEngine.builder()
                    .apiKey(tavilyKey)
                    .build();

            webRetriever = WebSearchContentRetriever.builder()
                    .webSearchEngine(tavily)
                    .build();
        }

        // --- QueryRouter : combine PDF + (optionnellement) Web ---

        QueryRouter queryRouter;
        if (webRetriever != null) {
            queryRouter = new DefaultQueryRouter(List.of(ragRetriever, autreRetriever, webRetriever));
        } else {
            queryRouter = new DefaultQueryRouter(List.of(ragRetriever, autreRetriever));
        }

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(queryRouter)
                .build();

        // --- Mémoire de conversation + Assistant ---

        chatMemory = MessageWindowChatMemory.withMaxMessages(30);

        assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .retrievalAugmentor(retrievalAugmentor) // branche le RAG
                .build();
    }

    // Ingestion d'une ressource (PDF / TXT) via Tika + splitter + embeddings
    private void ingestResource(String resourceName,
                                EmbeddingStore<TextSegment> store,
                                EmbeddingModel embeddingModel) {
        try {
            var url = LlmClient.class.getResource(resourceName);
            if (url == null) {
                System.err.println("Ressource introuvable : " + resourceName);
                return;
            }

            Path path = Paths.get(Objects.requireNonNull(url).toURI());

            ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
            Document document = FileSystemDocumentLoader.loadDocument(path, parser);

            DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
            List<TextSegment> segments = splitter.split(document);

            Response<List<Embedding>> response = embeddingModel.embedAll(segments);
            List<Embedding> embeddings = response.content();

            store.addAll(embeddings, segments);

        } catch (URISyntaxException e) {
            throw new RuntimeException("Erreur lors de l'ingestion de " + resourceName, e);
        }
    }

    // Configuration du logger LangChain4j
    private void configureLogger() {
        Logger packageLogger = Logger.getLogger("dev.langchain4j");
        packageLogger.setLevel(Level.FINE);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        packageLogger.addHandler(handler);
    }

    // === API utilisée par Bb ===

    public void setSystemRole(String role) {
        this.systemRole = role;
        chatMemory.clear();

        if (role != null && !role.trim().isEmpty()) {
            chatMemory.add(SystemMessage.from(role));
        }
    }

    public String ask(String prompt) {
        return assistant.chat(prompt);
    }

    public String getSystemRole() {
        return systemRole;
    }
}
