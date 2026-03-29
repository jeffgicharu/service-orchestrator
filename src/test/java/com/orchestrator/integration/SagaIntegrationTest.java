package com.orchestrator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SagaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Execute P2P transfer via HTTP returns COMPLETED")
    void executeTransfer_throughHttp() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"+254700000001\",\"receiver\":\"+254700000002\",\"amount\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalSteps").value(5));
    }

    @Test
    @DisplayName("Failed transfer returns CONFLICT with compensation")
    void executeTransfer_failure_returnsConflict() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"+254700000001\",\"receiver\":\"+254700000002\",\"amount\":5000,\"simulateFailure\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("COMPENSATED"));
    }

    @Test
    @DisplayName("Execute customer onboarding via HTTP")
    void executeOnboarding_throughHttp() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=CUSTOMER_ONBOARDING")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerName\":\"Jane\",\"phone\":\"+254700000003\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Execute payment reversal saga via HTTP")
    void executeReversal_throughHttp() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=PAYMENT_REVERSAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionId\":\"TXN-12345\",\"originalStatus\":\"COMPLETED\",\"amount\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Payment reversal with failure compensates correctly")
    void executeReversal_failure_compensates() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=PAYMENT_REVERSAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionId\":\"TXN-99\",\"originalStatus\":\"COMPLETED\",\"amount\":1000,\"failAtStep\":\"REFUND_SENDER_DEBIT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("COMPENSATED"));
    }

    @Test
    @DisplayName("Get saga by ID returns details")
    void getById_returnsDetails() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"s\",\"receiver\":\"r\",\"amount\":100}"))
                .andReturn();
        String sagaId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sagaId").asText();

        mockMvc.perform(get("/api/sagas/" + sagaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sagaId").value(sagaId));
    }

    @Test
    @DisplayName("Logs endpoint returns step execution history with context snapshots")
    void logs_returnsStepsWithContext() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"s\",\"receiver\":\"r\",\"amount\":200}"))
                .andReturn();
        String sagaId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sagaId").asText();

        mockMvc.perform(get("/api/sagas/" + sagaId + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stepName").exists())
                .andExpect(jsonPath("$[0].contextSnapshot").exists());
    }

    @Test
    @DisplayName("Retry a compensated saga creates new execution")
    void retry_compensatedSaga() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"+254700000001\",\"receiver\":\"+254700000002\",\"amount\":5000,\"simulateFailure\":true}"))
                .andReturn();
        String sagaId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sagaId").asText();

        // Retry creates a new saga execution (may succeed or fail depending on context)
        mockMvc.perform(post("/api/sagas/" + sagaId + "/retry"))
                .andExpect(jsonPath("$.sagaType").value("P2P_TRANSFER"))
                .andExpect(jsonPath("$.sagaId").exists());
    }

    @Test
    @DisplayName("List all sagas with pagination")
    void listAll_returnsPaginated() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=P2P_TRANSFER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sender\":\"s\",\"receiver\":\"r\",\"amount\":100}"));

        mockMvc.perform(get("/api/sagas?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Filter sagas by status")
    void filterByStatus_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/sagas/status/COMPLETED?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Filter sagas by type")
    void filterByType_returnsFiltered() throws Exception {
        mockMvc.perform(get("/api/sagas/type/P2P_TRANSFER?size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Stats returns per-type breakdown")
    void stats_returnsPerType() throws Exception {
        mockMvc.perform(get("/api/sagas/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.byType").exists())
                .andExpect(jsonPath("$.successRate").exists());
    }

    @Test
    @DisplayName("Types endpoint lists registered saga types and their steps")
    void types_listsRegistered() throws Exception {
        mockMvc.perform(get("/api/sagas/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='P2P_TRANSFER')]").exists())
                .andExpect(jsonPath("$[?(@.type=='CUSTOMER_ONBOARDING')]").exists())
                .andExpect(jsonPath("$[?(@.type=='PAYMENT_REVERSAL')]").exists());
    }

    @Test
    @DisplayName("Unknown saga type returns 400")
    void unknownType_returns400() throws Exception {
        mockMvc.perform(post("/api/sagas/execute?sagaType=FAKE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
