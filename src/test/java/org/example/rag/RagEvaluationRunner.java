package org.example.rag;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.client.MilvusClientFactory;
import org.example.config.ElasticsearchConfig;
import org.example.config.MilvusConfig;
import org.example.config.MilvusProperties;
import org.example.model.rag.MilvusSearchResult;
import org.example.service.EsKeywordSearchService;
import org.example.service.HybridRagSearchService;
import org.example.service.RerankService;
import org.example.service.VectorEmbeddingService;
import org.example.service.VectorSearchService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * RAG evaluation runner.
 *
 * <p>This class intentionally lives under src/test and does not require any production-code change.
 *
 * <p>Run example:
 * <pre>
 * mvn -DskipTests test-compile
 * mvn -Dexec.classpathScope=test -Dexec.mainClass=org.example.rag.RagEvaluationRunner exec:java
 * </pre>
 *
 * <p>Optional arguments:
 * <pre>
 * --dataset=D:\lpz\Desktop\rag_test_dataset_40.xlsx
 * --out=src/test/results
 * --topK=5
 * --limit=40
 * --retrievalOnly=false
 * </pre>
 */
public class RagEvaluationRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.0000");

    public static void main(String[] args) throws Exception {
        EvalOptions options = EvalOptions.fromArgs(args);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EvalSpringConfig.class)
                .web(WebApplicationType.NONE)
                .run(args)) {

            Environment environment = context.getEnvironment();
            VectorSearchService vectorSearchService = context.getBean(VectorSearchService.class);
            HybridRagSearchService hybridRagSearchService = context.getBean(HybridRagSearchService.class);

            XlsxDatasetReader datasetReader = new XlsxDatasetReader(MAPPER);
            List<TestCase> cases = datasetReader.read(options.datasetPath);
            if (cases.isEmpty()) {
                throw new IllegalStateException("No test cases were loaded from " + options.datasetPath.toAbsolutePath());
            }
            if (options.limit > 0 && options.limit < cases.size()) {
                cases = new ArrayList<>(cases.subList(0, options.limit));
            }
            System.out.printf(Locale.ROOT, "Loaded %d test cases from %s%n", cases.size(), options.datasetPath);

            DashScopeRagGenerator generator = new DashScopeRagGenerator(environment);
            RagAsLlmEvaluator llmEvaluator = new RagAsLlmEvaluator(environment);

            List<CaseResult> results = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                TestCase testCase = cases.get(i);
                System.out.printf(Locale.ROOT, "[%d/%d] %s%n", i + 1, cases.size(), testCase.question());

                List<MilvusSearchResult> vectorOnlyResults =
                        vectorSearchService.searchSimilarDocuments(testCase.question(), options.topK, false);
                RetrievalMetrics vectorMetrics = RetrievalMetrics.compute(
                        testCase.goldChunkIds(),
                        idsOf(vectorOnlyResults),
                        options.topK
                );

                List<MilvusSearchResult> hybridRerankResults =
                        hybridRagSearchService.search(testCase.question(), options.topK);
                RetrievalMetrics hybridMetrics = RetrievalMetrics.compute(
                        testCase.goldChunkIds(),
                        idsOf(hybridRerankResults),
                        options.topK
                );

                String generatedAnswer = "";
                LlmScores llmScores = LlmScores.empty();
                if (!options.retrievalOnly) {
                    generatedAnswer = generator.generate(testCase.question(), hybridRerankResults);
                    llmScores = llmEvaluator.evaluate(
                            testCase.question(),
                            testCase.goldAnswer(),
                            generatedAnswer,
                            hybridRerankResults
                    );
                }

                results.add(new CaseResult(
                        testCase,
                        toRetrievedDocs(vectorOnlyResults),
                        vectorMetrics,
                        toRetrievedDocs(hybridRerankResults),
                        hybridMetrics,
                        generatedAnswer,
                        llmScores
                ));
            }

            EvalReport report = EvalReport.from(results, options);
            Files.createDirectories(options.outputDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path jsonPath = options.outputDir.resolve("rag-eval-" + timestamp + ".json");
            Path csvPath = options.outputDir.resolve("rag-eval-" + timestamp + ".csv");
            MAPPER.writeValue(jsonPath.toFile(), report);
            CsvWriter.write(csvPath, report);

            printSummary(report, jsonPath, csvPath);
        }
    }

    private static void printSummary(EvalReport report, Path jsonPath, Path csvPath) {
        System.out.println();
        System.out.println("========== RAG Evaluation Summary ==========");
        System.out.println("cases: " + report.summary().caseCount());
        System.out.println("vector_only.recall@5: " + SCORE_FORMAT.format(report.summary().vectorOnly().recallAtK()));
        System.out.println("vector_only.mrr:      " + SCORE_FORMAT.format(report.summary().vectorOnly().mrr()));
        System.out.println("hybrid_rerank.recall@5: " + SCORE_FORMAT.format(report.summary().hybridRerank().recallAtK()));
        System.out.println("hybrid_rerank.mrr:      " + SCORE_FORMAT.format(report.summary().hybridRerank().mrr()));
        if (report.summary().llmScores() != null) {
            System.out.println("llm.faithfulness:       " + SCORE_FORMAT.format(report.summary().llmScores().faithfulness()));
            System.out.println("llm.answer_relevancy:   " + SCORE_FORMAT.format(report.summary().llmScores().answerRelevancy()));
            System.out.println("llm.context_precision:  " + SCORE_FORMAT.format(report.summary().llmScores().contextPrecision()));
            System.out.println("llm.context_recall:     " + SCORE_FORMAT.format(report.summary().llmScores().contextRecall()));
            System.out.println("llm.overall:            " + SCORE_FORMAT.format(report.summary().llmScores().overall()));
        }
        System.out.println("json: " + jsonPath.toAbsolutePath());
        System.out.println("csv:  " + csvPath.toAbsolutePath());
    }

    private static List<String> idsOf(List<MilvusSearchResult> results) {
        return results.stream()
                .map(MilvusSearchResult::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<RetrievedDoc> toRetrievedDocs(List<MilvusSearchResult> results) {
        List<RetrievedDoc> docs = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            MilvusSearchResult result = results.get(i);
            docs.add(new RetrievedDoc(
                    i + 1,
                    result.getId(),
                    result.getScore(),
                    safeSnippet(result.getContent(), 600),
                    result.getMetadata()
            ));
        }
        return docs;
    }

    private static String safeSnippet(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    @Configuration
    @EnableConfigurationProperties(MilvusProperties.class)
    @Import({
            MilvusClientFactory.class,
            MilvusConfig.class,
            ElasticsearchConfig.class,
            VectorEmbeddingService.class,
            VectorSearchService.class,
            EsKeywordSearchService.class,
            RerankService.class,
            HybridRagSearchService.class
    })
    static class EvalSpringConfig {

        @Bean
        ObjectMapper objectMapper() {
            return MAPPER;
        }

        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }
    }

    record EvalOptions(
            Path datasetPath,
            Path outputDir,
            int topK,
            int limit,
            boolean retrievalOnly
    ) {
        static EvalOptions fromArgs(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) {
                    continue;
                }
                int split = arg.indexOf('=');
                values.put(arg.substring(2, split), arg.substring(split + 1));
            }

            return new EvalOptions(
                    Path.of(values.getOrDefault("dataset", "D:\\lpz\\Desktop\\rag_test_dataset_40.xlsx")),
                    Path.of(values.getOrDefault("out", "src/test/results")),
                    Integer.parseInt(values.getOrDefault("topK", "5")),
                    Integer.parseInt(values.getOrDefault("limit", "40")),
                    Boolean.parseBoolean(values.getOrDefault("retrievalOnly", "false"))
            );
        }
    }

    record TestCase(
            String id,
            String question,
            List<String> goldChunkIds,
            String goldAnswer,
            String category,
            String difficulty,
            int goldChunkCount
    ) {
    }

    record RetrievedDoc(
            int rank,
            String id,
            float score,
            String content,
            String metadata
    ) {
    }

    record RetrievalMetrics(
            double recallAtK,
            double mrr,
            int firstRelevantRank,
            int hitCount
    ) {
        static RetrievalMetrics compute(List<String> goldIds, List<String> retrievedIds, int topK) {
            if (goldIds == null || goldIds.isEmpty()) {
                return new RetrievalMetrics(0.0, 0.0, 0, 0);
            }

            Set<String> goldSet = new LinkedHashSet<>(goldIds);
            int limit = Math.min(topK, retrievedIds.size());
            int hitCount = 0;
            int firstRelevantRank = 0;
            Set<String> seenRelevant = new HashSet<>();

            for (int i = 0; i < limit; i++) {
                String retrievedId = retrievedIds.get(i);
                if (goldSet.contains(retrievedId) && seenRelevant.add(retrievedId)) {
                    hitCount++;
                    if (firstRelevantRank == 0) {
                        firstRelevantRank = i + 1;
                    }
                }
            }

            double recallAtK = hitCount / (double) goldSet.size();
            double mrr = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;
            return new RetrievalMetrics(recallAtK, mrr, firstRelevantRank, hitCount);
        }
    }

    record LlmScores(
            double faithfulness,
            double answerRelevancy,
            double contextPrecision,
            double contextRecall,
            double overall,
            String rationale
    ) {
        static LlmScores empty() {
            return new LlmScores(0.0, 0.0, 0.0, 0.0, 0.0, "");
        }

        boolean hasScore() {
            return overall > 0.0
                    || faithfulness > 0.0
                    || answerRelevancy > 0.0
                    || contextPrecision > 0.0
                    || contextRecall > 0.0;
        }
    }

    record CaseResult(
            TestCase testCase,
            List<RetrievedDoc> vectorOnlyRetrieved,
            RetrievalMetrics vectorOnlyMetrics,
            List<RetrievedDoc> hybridRerankRetrieved,
            RetrievalMetrics hybridRerankMetrics,
            String generatedAnswer,
            LlmScores llmScores
    ) {
    }

    record RetrievalSummary(double recallAtK, double mrr) {
    }

    record EvalSummary(
            int caseCount,
            RetrievalSummary vectorOnly,
            RetrievalSummary hybridRerank,
            LlmScores llmScores
    ) {
    }

    record EvalReport(
            EvalOptions options,
            EvalSummary summary,
            List<CaseResult> cases
    ) {
        static EvalReport from(List<CaseResult> results, EvalOptions options) {
            RetrievalSummary vectorOnly = summarizeRetrieval(
                    results.stream().map(CaseResult::vectorOnlyMetrics).toList()
            );
            RetrievalSummary hybridRerank = summarizeRetrieval(
                    results.stream().map(CaseResult::hybridRerankMetrics).toList()
            );
            LlmScores llmScores = summarizeLlm(
                    results.stream().map(CaseResult::llmScores).filter(LlmScores::hasScore).toList()
            );
            return new EvalReport(
                    options,
                    new EvalSummary(results.size(), vectorOnly, hybridRerank, llmScores),
                    results
            );
        }

        private static RetrievalSummary summarizeRetrieval(List<RetrievalMetrics> metrics) {
            if (metrics.isEmpty()) {
                return new RetrievalSummary(0.0, 0.0);
            }
            return new RetrievalSummary(
                    metrics.stream().mapToDouble(RetrievalMetrics::recallAtK).average().orElse(0.0),
                    metrics.stream().mapToDouble(RetrievalMetrics::mrr).average().orElse(0.0)
            );
        }

        private static LlmScores summarizeLlm(List<LlmScores> scores) {
            if (scores.isEmpty()) {
                return null;
            }
            return new LlmScores(
                    scores.stream().mapToDouble(LlmScores::faithfulness).average().orElse(0.0),
                    scores.stream().mapToDouble(LlmScores::answerRelevancy).average().orElse(0.0),
                    scores.stream().mapToDouble(LlmScores::contextPrecision).average().orElse(0.0),
                    scores.stream().mapToDouble(LlmScores::contextRecall).average().orElse(0.0),
                    scores.stream().mapToDouble(LlmScores::overall).average().orElse(0.0),
                    "macro_average"
            );
        }
    }

    static class XlsxDatasetReader {

        private final ObjectMapper objectMapper;

        XlsxDatasetReader(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        List<TestCase> read(Path workbookPath) throws Exception {
            if (!Files.exists(workbookPath)) {
                throw new IllegalArgumentException("Dataset not found: " + workbookPath.toAbsolutePath());
            }

            try (ZipFile zipFile = new ZipFile(workbookPath.toFile(), StandardCharsets.UTF_8)) {
                List<String> sharedStrings = readSharedStrings(zipFile);
                String sheetPath = resolveSheetPath(zipFile, "RAG测试集");
                List<Map<String, String>> rows = readSheetRows(zipFile, sheetPath, sharedStrings);
                List<TestCase> cases = new ArrayList<>();

                for (Map<String, String> row : rows) {
                    String question = row.getOrDefault("question", "").trim();
                    if (question.isEmpty()) {
                        continue;
                    }

                    List<String> goldChunkIds = objectMapper.readValue(
                            row.getOrDefault("gold_chunk_ids", "[]"),
                            new TypeReference<>() {
                            }
                    );
                    int goldChunkCount = parseInt(row.get("gold_chunk_count"), goldChunkIds.size());
                    cases.add(new TestCase(
                            row.getOrDefault("id", UUID.randomUUID().toString()),
                            question,
                            goldChunkIds,
                            row.getOrDefault("gold_answer", ""),
                            row.getOrDefault("category", ""),
                            row.getOrDefault("difficulty", ""),
                            goldChunkCount
                    ));
                }

                return cases;
            }
        }

        private List<String> readSharedStrings(ZipFile zipFile) throws Exception {
            ZipEntry entry = zipFile.getEntry("xl/sharedStrings.xml");
            if (entry == null) {
                return List.of();
            }

            Document document;
            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                document = xml(inputStream);
            }

            List<String> values = new ArrayList<>();
            NodeList items = documentElements(document, "si");
            for (int i = 0; i < items.getLength(); i++) {
                values.add(textContent((Element) items.item(i)));
            }
            return values;
        }

        private String resolveSheetPath(ZipFile zipFile, String expectedName) throws Exception {
            Document workbook;
            try (InputStream inputStream = zipFile.getInputStream(requiredEntry(zipFile, "xl/workbook.xml"))) {
                workbook = xml(inputStream);
            }
            Document relationships;
            try (InputStream inputStream = zipFile.getInputStream(requiredEntry(zipFile, "xl/_rels/workbook.xml.rels"))) {
                relationships = xml(inputStream);
            }

            Map<String, String> targetByRelationshipId = new HashMap<>();
            NodeList relationshipNodes = documentElements(relationships, "Relationship");
            for (int i = 0; i < relationshipNodes.getLength(); i++) {
                Element relationship = (Element) relationshipNodes.item(i);
                targetByRelationshipId.put(relationship.getAttribute("Id"), relationship.getAttribute("Target"));
            }

            NodeList sheetNodes = documentElements(workbook, "sheet");
            for (int i = 0; i < sheetNodes.getLength(); i++) {
                Element sheet = (Element) sheetNodes.item(i);
                if (!expectedName.equals(sheet.getAttribute("name"))) {
                    continue;
                }
                String relationshipId = sheet.getAttributeNS(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id"
                );
                if (relationshipId == null || relationshipId.isBlank()) {
                    relationshipId = sheet.getAttribute("r:id");
                }
                String target = targetByRelationshipId.get(relationshipId);
                if (target == null || target.isBlank()) {
                    break;
                }
                if (target.startsWith("/")) {
                    return target.substring(1);
                }
                return "xl/" + target;
            }

            return "xl/worksheets/sheet1.xml";
        }

        private List<Map<String, String>> readSheetRows(
                ZipFile zipFile,
                String sheetPath,
                List<String> sharedStrings
        ) throws Exception {
            Document sheet;
            try (InputStream inputStream = zipFile.getInputStream(requiredEntry(zipFile, sheetPath))) {
                sheet = xml(inputStream);
            }

            NodeList rowNodes = documentElements(sheet, "row");
            List<String> headers = new ArrayList<>();
            List<Map<String, String>> rows = new ArrayList<>();

            for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
                Element row = (Element) rowNodes.item(rowIndex);
                Map<Integer, String> valuesByColumn = new LinkedHashMap<>();
                NodeList cellNodes = elementElements(row, "c");

                for (int cellIndex = 0; cellIndex < cellNodes.getLength(); cellIndex++) {
                    Element cell = (Element) cellNodes.item(cellIndex);
                    int columnIndex = columnIndex(cell.getAttribute("r"));
                    valuesByColumn.put(columnIndex, readCell(cell, sharedStrings));
                }

                if (headers.isEmpty()) {
                    for (int i = 0; i <= valuesByColumn.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1); i++) {
                        headers.add(valuesByColumn.getOrDefault(i, ""));
                    }
                    continue;
                }

                Map<String, String> mapped = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String header = headers.get(i);
                    if (header == null || header.isBlank()) {
                        continue;
                    }
                    mapped.put(header, valuesByColumn.getOrDefault(i, ""));
                }
                rows.add(mapped);
            }

            return rows;
        }

        private String readCell(Element cell, List<String> sharedStrings) {
            String type = cell.getAttribute("t");
            if ("inlineStr".equals(type)) {
                NodeList inlineNodes = elementElements(cell, "is");
                return inlineNodes.getLength() == 0 ? "" : textContent((Element) inlineNodes.item(0));
            }

            String value = firstChildText(cell, "v");
            if ("s".equals(type)) {
                int sharedStringIndex = parseInt(value, -1);
                if (sharedStringIndex >= 0 && sharedStringIndex < sharedStrings.size()) {
                    return sharedStrings.get(sharedStringIndex);
                }
                return "";
            }
            return value == null ? "" : value;
        }

        private ZipEntry requiredEntry(ZipFile zipFile, String name) {
            ZipEntry entry = zipFile.getEntry(name);
            if (entry == null) {
                throw new IllegalArgumentException("Missing xlsx entry: " + name);
            }
            return entry;
        }

        private Document xml(InputStream inputStream) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(inputStream);
        }

        private String textContent(Element element) {
            NodeList textNodes = elementElements(element, "t");
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < textNodes.getLength(); i++) {
                builder.append(textNodes.item(i).getTextContent());
            }
            return builder.toString();
        }

        private String firstChildText(Element element, String tagName) {
            NodeList nodes = elementElements(element, tagName);
            if (nodes.getLength() == 0) {
                return "";
            }
            Node node = nodes.item(0);
            return node == null ? "" : node.getTextContent();
        }

        private int columnIndex(String cellReference) {
            int column = 0;
            for (int i = 0; i < cellReference.length(); i++) {
                char ch = cellReference.charAt(i);
                if (!Character.isLetter(ch)) {
                    break;
                }
                column = column * 26 + (Character.toUpperCase(ch) - 'A' + 1);
            }
            return column - 1;
        }

        private NodeList documentElements(Document document, String localName) {
            NodeList nodes = document.getElementsByTagNameNS("*", localName);
            if (nodes.getLength() > 0) {
                return nodes;
            }
            return document.getElementsByTagName(localName);
        }

        private NodeList elementElements(Element element, String localName) {
            NodeList nodes = element.getElementsByTagNameNS("*", localName);
            if (nodes.getLength() > 0) {
                return nodes;
            }
            return element.getElementsByTagName(localName);
        }
    }

    static class DashScopeRagGenerator {

        private final Generation generation = new Generation();
        private final String apiKey;
        private final String model;

        DashScopeRagGenerator(Environment environment) {
            this.apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
            this.model = environment.getProperty("rag.model", "qwen3-max");
            Constants.apiKey = apiKey;
            Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        }

        String generate(String question, List<MilvusSearchResult> retrievedDocs) {
            String prompt = """
                    你是一个专业、严谨的 RAG 问答助手。请只基于给定参考资料回答用户问题。
                    如果参考资料不足以支持答案，请明确说明资料不足，不要编造。

                    用户问题：
                    %s

                    参考资料：
                    %s
                    """.formatted(question, buildContext(retrievedDocs));
            return call(prompt);
        }

        private String call(String prompt) {
            try {
                GenerationParam param = GenerationParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .resultFormat("message")
                        .messages(List.of(Message.builder()
                                .role(Role.USER.getValue())
                                .content(prompt)
                                .build()))
                        .build();
                GenerationResult result = generation.call(param);
                if (result == null
                        || result.getOutput() == null
                        || result.getOutput().getChoices() == null
                        || result.getOutput().getChoices().isEmpty()) {
                    return "";
                }
                return result.getOutput().getChoices().get(0).getMessage().getContent();
            } catch (Exception e) {
                System.err.println("answer generation failed: " + e.getMessage());
                return "";
            }
        }
    }

    static class RagAsLlmEvaluator {

        private final Generation generation = new Generation();
        private final ObjectMapper objectMapper = MAPPER;
        private final String apiKey;
        private final String model;

        RagAsLlmEvaluator(Environment environment) {
            this.apiKey = environment.getProperty("spring.ai.dashscope.api-key", "");
            this.model = environment.getProperty("rag.eval.model",
                    environment.getProperty("rag.model", "qwen3-max"));
            Constants.apiKey = apiKey;
            Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        }

        LlmScores evaluate(
                String question,
                String goldAnswer,
                String generatedAnswer,
                List<MilvusSearchResult> retrievedDocs
        ) {
            if (generatedAnswer == null || generatedAnswer.isBlank()) {
                return LlmScores.empty();
            }

            String prompt = """
                    你是 RAG 评测裁判。请参考 RAGAS 的四个维度，对一次 RAG 问答进行 0 到 1 打分。

                    维度定义：
                    1. faithfulness：生成答案是否被检索上下文支持，是否存在幻觉。
                    2. answer_relevancy：生成答案是否直接、完整地回答用户问题。
                    3. context_precision：检索上下文中排在前面的内容是否真正相关，噪声是否少。
                    4. context_recall：检索上下文是否覆盖 gold_answer 所需的关键信息。

                    打分要求：
                    - 每个分数必须是 0 到 1 的小数。
                    - overall 为四个维度的算术平均。
                    - 只输出 JSON，不要输出 markdown，不要输出额外解释。

                    JSON schema：
                    {
                      "faithfulness": 0.0,
                      "answer_relevancy": 0.0,
                      "context_precision": 0.0,
                      "context_recall": 0.0,
                      "overall": 0.0,
                      "rationale": "一句中文理由"
                    }

                    用户问题：
                    %s

                    Gold Answer：
                    %s

                    检索上下文：
                    %s

                    生成答案：
                    %s
                    """.formatted(question, goldAnswer, buildContext(retrievedDocs), generatedAnswer);

            try {
                String response = call(prompt);
                JsonNode root = objectMapper.readTree(extractJson(response));
                double faithfulness = score(root, "faithfulness");
                double answerRelevancy = score(root, "answer_relevancy");
                double contextPrecision = score(root, "context_precision");
                double contextRecall = score(root, "context_recall");
                double overall = (faithfulness + answerRelevancy + contextPrecision + contextRecall) / 4.0;
                return new LlmScores(
                        faithfulness,
                        answerRelevancy,
                        contextPrecision,
                        contextRecall,
                        overall,
                        root.path("rationale").asText("")
                );
            } catch (Exception e) {
                System.err.println("LLM evaluation failed: " + e.getMessage());
                return LlmScores.empty();
            }
        }

        private String call(String prompt) throws Exception {
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .resultFormat("message")
                    .messages(List.of(Message.builder()
                            .role(Role.USER.getValue())
                            .content(prompt)
                            .build()))
                    .build();
            GenerationResult result = generation.call(param);
            if (result == null
                    || result.getOutput() == null
                    || result.getOutput().getChoices() == null
                    || result.getOutput().getChoices().isEmpty()) {
                return "{}";
            }
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        }

        private String extractJson(String response) {
            if (response == null || response.isBlank()) {
                return "{}";
            }
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end < start) {
                return "{}";
            }
            return response.substring(start, end + 1);
        }

        private double score(JsonNode root, String fieldName) {
            return Math.max(0.0, Math.min(1.0, root.path(fieldName).asDouble(0.0)));
        }
    }

    static class CsvWriter {

        static void write(Path csvPath, EvalReport report) throws Exception {
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                writer.write(String.join(",",
                        "id",
                        "category",
                        "difficulty",
                        "question",
                        "gold_chunk_ids",
                        "vector_only_ids",
                        "vector_recall_at_5",
                        "vector_mrr",
                        "hybrid_rerank_ids",
                        "hybrid_rerank_recall_at_5",
                        "hybrid_rerank_mrr",
                        "faithfulness",
                        "answer_relevancy",
                        "context_precision",
                        "context_recall",
                        "overall",
                        "rationale"
                ));
                writer.newLine();

                for (CaseResult result : report.cases()) {
                    List<String> columns = List.of(
                            result.testCase().id(),
                            result.testCase().category(),
                            result.testCase().difficulty(),
                            result.testCase().question(),
                            String.join("|", result.testCase().goldChunkIds()),
                            ids(result.vectorOnlyRetrieved()),
                            SCORE_FORMAT.format(result.vectorOnlyMetrics().recallAtK()),
                            SCORE_FORMAT.format(result.vectorOnlyMetrics().mrr()),
                            ids(result.hybridRerankRetrieved()),
                            SCORE_FORMAT.format(result.hybridRerankMetrics().recallAtK()),
                            SCORE_FORMAT.format(result.hybridRerankMetrics().mrr()),
                            SCORE_FORMAT.format(result.llmScores().faithfulness()),
                            SCORE_FORMAT.format(result.llmScores().answerRelevancy()),
                            SCORE_FORMAT.format(result.llmScores().contextPrecision()),
                            SCORE_FORMAT.format(result.llmScores().contextRecall()),
                            SCORE_FORMAT.format(result.llmScores().overall()),
                            result.llmScores().rationale()
                    );
                    writer.write(columns.stream().map(CsvWriter::csv).collect(Collectors.joining(",")));
                    writer.newLine();
                }
            }
        }

        private static String ids(List<RetrievedDoc> docs) {
            return docs.stream().map(RetrievedDoc::id).collect(Collectors.joining("|"));
        }

        private static String csv(String value) {
            if (value == null) {
                return "";
            }
            String escaped = value.replace("\"", "\"\"");
            if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
                return "\"" + escaped + "\"";
            }
            return escaped;
        }
    }

    private static String buildContext(List<MilvusSearchResult> retrievedDocs) {
        if (retrievedDocs == null || retrievedDocs.isEmpty()) {
            return "无检索上下文。";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < retrievedDocs.size(); i++) {
            MilvusSearchResult result = retrievedDocs.get(i);
            builder.append("【文档").append(i + 1).append("】id=")
                    .append(result.getId()).append('\n')
                    .append(result.getContent()).append("\n\n");
        }
        return builder.toString();
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int integerProperty(Environment environment, String key, int fallback) {
        return parseInt(environment.getProperty(key), fallback);
    }

    private static double doubleProperty(Environment environment, String key, double fallback) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
