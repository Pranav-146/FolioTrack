package com.foliotrack.web;

import com.foliotrack.dto.AskResponse;
import com.foliotrack.exception.LlmUnavailableException;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.exception.QueryRejectedException;
import com.foliotrack.service.AskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.foliotrack.web.ApiErrorMatchers.apiError;
import static com.foliotrack.web.ApiErrorMatchers.apiErrorWithoutFieldErrors;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AskController.class)
class AskControllerTest {

    private static final String ASK_URL = "/api/users/1/ask";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AskService askService;

    @Test
    void returns200WithSqlAndAnswer() throws Exception {
        when(askService.ask(1L, "How much cash do I have?")).thenReturn(new AskResponse("How much cash do I have?",
                "SELECT cash_balance FROM v_my_account", 1, "You have $100,000.00 in cash."));

        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"How much cash do I have?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("How much cash do I have?"))
                .andExpect(jsonPath("$.sql").value("SELECT cash_balance FROM v_my_account"))
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.answer").value("You have $100,000.00 in cash."));
    }

    @Test
    void blankQuestionIs400WithFieldError() throws Exception {
        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ASK_URL))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"));

        verifyNoInteractions(askService);
    }

    @Test
    void overlongQuestionIs400() throws Exception {
        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + "a".repeat(501) + "\"}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ASK_URL))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("question"));

        verifyNoInteractions(askService);
    }

    @Test
    void unknownUserIs404() throws Exception {
        when(askService.ask(anyLong(), anyString())).thenThrow(new NotFoundException("User not found: 1"));

        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"How much cash do I have?\"}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, ASK_URL));
    }

    @Test
    void rejectedSqlIs422WithSafeMessage() throws Exception {
        when(askService.ask(anyLong(), anyString()))
                .thenThrow(new QueryRejectedException("Query may only read from [v_my_account], not: users"));

        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"Show me every user's email\"}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.UNPROCESSABLE_ENTITY, ASK_URL))
                .andExpect(jsonPath("$.message").value("Query may only read from [v_my_account], not: users"));
    }

    @Test
    void llmOutageIs503() throws Exception {
        when(askService.ask(anyLong(), anyString()))
                .thenThrow(new LlmUnavailableException("The AI assistant is unavailable right now"));

        mockMvc.perform(post(ASK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"How much cash do I have?\"}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.SERVICE_UNAVAILABLE, ASK_URL));
    }
}
