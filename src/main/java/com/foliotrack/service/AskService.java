package com.foliotrack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foliotrack.dto.AskResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.exception.QueryRejectedException;
import com.foliotrack.llm.LlmClient;
import com.foliotrack.repository.ReadOnlyQueryExecutor;
import com.foliotrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "Ask your portfolio": question -> LLM writes SQL -> validate -> run read-only as this user -> LLM summarizes.
// No @Transactional here: the query runs in its own read-only transaction on a separate pool (see
// ReadOnlyQueryExecutor), and we don't want a DB transaction held open while waiting on two LLM calls.
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    // The prompt tells the model to output this when the views can't answer the question
    private static final String CANNOT_ANSWER = "CANNOT_ANSWER";
    private static final String OUT_OF_SCOPE_ANSWER = "I can only answer questions about your own cash, holdings, "
            + "orders and transactions. Try asking something like \"What is my biggest holding?\"";

    // Models often wrap SQL in ```sql ... ``` despite being told not to; take what's inside the fence
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:sql)?\\s*(.*?)\\s*```",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private final LlmClient llmClient;
    private final SqlValidator sqlValidator;
    private final ReadOnlyQueryExecutor queryExecutor;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final String sqlPrompt;
    private final String answerPrompt;

    // Prompts live in versioned resource files: a prompt change is a reviewed diff, and v2 can sit beside v1
    public AskService(LlmClient llmClient, SqlValidator sqlValidator, ReadOnlyQueryExecutor queryExecutor,
                      UserRepository userRepository, ObjectMapper objectMapper,
                      @Value("classpath:prompts/ask-sql-v1.txt") Resource sqlPrompt,
                      @Value("classpath:prompts/ask-answer-v1.txt") Resource answerPrompt) {
        this.llmClient = llmClient;
        this.sqlValidator = sqlValidator;
        this.queryExecutor = queryExecutor;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.sqlPrompt = read(sqlPrompt);
        this.answerPrompt = read(answerPrompt);
    }

    public AskResponse ask(long userId, String question) {
        // Checked first so an unknown user costs no LLM call
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found: " + userId);
        }

        String sql = extractSql(llmClient.complete(sqlPrompt, question));
        if (sql.equals(CANNOT_ANSWER)) {
            return new AskResponse(question, null, 0, OUT_OF_SCOPE_ANSWER);
        }
        log.info("Ask from user {}: generated SQL: {}", userId, sql);

        String safeSql;
        try {
            safeSql = sqlValidator.validateAndWrap(sql);
        } catch (QueryRejectedException e) {
            log.warn("Rejected generated SQL for user {} ({}): {}", userId, e.getMessage(), sql);
            throw e;
        }

        List<Map<String, Object>> rows;
        try {
            rows = queryExecutor.run(userId, safeSql);
        } catch (DataAccessException e) {
            // e.g. the model invented a column, hit the timeout, or (if the validator ever missed something)
            // the read-only role was denied. The DB message is logged, never returned.
            log.warn("Generated SQL failed for user {}: {}", userId, e.getMostSpecificCause().getMessage());
            throw new QueryRejectedException("The generated query could not be run; try rephrasing the question");
        }

        String answer = llmClient.complete(answerPrompt, """
                Question: %s
                SQL: %s
                Result rows (JSON): %s""".formatted(question, sql, toJson(rows)));
        return new AskResponse(question, sql, rows.size(), answer);
    }

    private static String extractSql(String reply) {
        String sql = reply.strip();
        Matcher fence = CODE_FENCE.matcher(sql);
        if (fence.find()) {
            sql = fence.group(1).strip();
        }
        // A single trailing ';' is harmless formatting. Any other ';' stays and the validator rejects it.
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }
        return sql;
    }

    private String toJson(List<Map<String, Object>> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize query result", e);
        }
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read prompt " + resource.getDescription(), e);
        }
    }
}
