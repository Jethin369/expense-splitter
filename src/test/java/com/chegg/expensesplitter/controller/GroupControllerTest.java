package com.chegg.expensesplitter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack API tests (controller -> service -> repository -> H2) using MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createGroupReturns201WithCreatedGroup() throws Exception {
        Map<String, Object> request = Map.of("name", "Goa Trip", "members", List.of("Alice", "Bob", "Carol"));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Goa Trip"))
                .andExpect(jsonPath("$.members", hasSize(3)))
                .andExpect(jsonPath("$.members", containsInAnyOrder("Alice", "Bob", "Carol")));
    }

    @Test
    void createGroupWithMissingNameReturns400() throws Exception {
        Map<String, Object> request = Map.of("members", List.of("Alice", "Bob"));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void createGroupWithEmptyMembersReturns400() throws Exception {
        Map<String, Object> request = Map.of("name", "Goa Trip", "members", List.of());

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getGroupThatDoesNotExistReturns404() throws Exception {
        mockMvc.perform(get("/api/groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void addingExpenseAppearsInExpenseList() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob", "Carol"));

        Map<String, Object> expenseRequest = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", List.of("Alice", "Bob", "Carol")
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hotel"))
                .andExpect(jsonPath("$.amount").value(3000.00));

        mockMvc.perform(get("/api/groups/" + groupId + "/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Hotel"));
    }

    @Test
    void addingExpenseWithPaidByNotInGroupReturns422() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob"));

        Map<String, Object> expenseRequest = Map.of(
                "title", "Hotel",
                "amount", 100.00,
                "paidBy", "Dave",
                "splitAmong", List.of("Alice", "Bob")
        );

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void addingExpenseWithEmptySplitAmongReturns400() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob"));

        Map<String, Object> expenseRequest = Map.of(
                "title", "Hotel",
                "amount", 100.00,
                "paidBy", "Alice",
                "splitAmong", List.of()
        );

        // Bean validation (@NotEmpty) rejects this before it reaches the service layer -> 400
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expenseRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void balancesReflectMultipleExpenses() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob", "Carol"));
        addExpense(groupId, "Hotel", 3000.00, "Alice", List.of("Alice", "Bob", "Carol"));

        mockMvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.member=='Alice')].netBalance").value(2000.00))
                .andExpect(jsonPath("$.balances[?(@.member=='Bob')].netBalance").value(-1000.00))
                .andExpect(jsonPath("$.balances[?(@.member=='Carol')].netBalance").value(-1000.00));
    }

    @Test
    void settlementsEndpointReturnsSimplifiedTransactions() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob", "Carol"));
        addExpense(groupId, "Hotel", 3000.00, "Alice", List.of("Alice", "Bob", "Carol"));

        mockMvc.perform(get("/api/groups/" + groupId + "/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements", hasSize(2)))
                .andExpect(jsonPath("$.settlements[*].to", everyItem(is("Alice"))));
    }

    @Test
    void deletingExpenseReturns204AndUpdatesBalances() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob", "Carol"));
        String expenseId = addExpense(groupId, "Hotel", 3000.00, "Alice", List.of("Alice", "Bob", "Carol"));

        mockMvc.perform(delete("/api/groups/" + groupId + "/expenses/" + expenseId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId + "/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[?(@.member=='Alice')].netBalance").value(0.00));
    }

    @Test
    void deletingNonExistentExpenseReturns404() throws Exception {
        String groupId = createGroupAndGetId("Goa Trip", List.of("Alice", "Bob"));

        mockMvc.perform(delete("/api/groups/" + groupId + "/expenses/999999"))
                .andExpect(status().isNotFound());
    }

    private String createGroupAndGetId(String name, List<String> members) throws Exception {
        Map<String, Object> request = Map.of("name", name, "members", members);
        String response = mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf(objectMapper.readTree(response).get("id").asLong());
    }

    private String addExpense(String groupId, String title, double amount, String paidBy, List<String> splitAmong) throws Exception {
        Map<String, Object> request = Map.of(
                "title", title, "amount", amount, "paidBy", paidBy, "splitAmong", splitAmong);
        String response = mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return String.valueOf(objectMapper.readTree(response).get("id").asLong());
    }
}
