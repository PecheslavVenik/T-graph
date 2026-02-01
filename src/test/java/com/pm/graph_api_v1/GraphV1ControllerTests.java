package com.pm.graph_api_v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:duckdb:./target/graph_test.duckdb",
        "spring.datasource.driver-class-name=org.duckdb.DuckDBDriver"
})
class GraphV1ControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedData() {
        jdbcTemplate.execute("DELETE FROM node_lookup");
        jdbcTemplate.execute("DELETE FROM edges");
        jdbcTemplate.execute("DELETE FROM nodes");

        jdbcTemplate.update(
                "INSERT INTO nodes (id, kind, label, attrs, flags) VALUES (?, ?, ?, ?, ?)",
                "person:1", "person", "Alice", "{\"age\":30}", "[\"vip\"]"
        );
        jdbcTemplate.update(
                "INSERT INTO nodes (id, kind, label, attrs, flags) VALUES (?, ?, ?, ?, ?)",
                "person:2", "person", "Bob", "{}", "[]"
        );
        jdbcTemplate.update(
                "INSERT INTO nodes (id, kind, label, attrs, flags) VALUES (?, ?, ?, ?, ?)",
                "phone:+7999", "phone", "+7999", "{}", "[]"
        );
        jdbcTemplate.update(
                "INSERT INTO nodes (id, kind, label, attrs, flags) VALUES (?, ?, ?, ?, ?)",
                "company:7700000000", "company", "Acme", "{}", "[]"
        );

        jdbcTemplate.update(
                "INSERT INTO node_lookup (lookup_kind, lookup_value, node_id) VALUES (?, ?, ?)",
                "id", "1", "person:1"
        );
        jdbcTemplate.update(
                "INSERT INTO node_lookup (lookup_kind, lookup_value, node_id) VALUES (?, ?, ?)",
                "phone_no", "+7999", "phone:+7999"
        );
        jdbcTemplate.update(
                "INSERT INTO node_lookup (lookup_kind, lookup_value, node_id) VALUES (?, ?, ?)",
                "party_rk", "7700000000", "company:7700000000"
        );

        jdbcTemplate.update(
                "INSERT INTO edges (id, src, dst, kind, attrs, flags) VALUES (?, ?, ?, ?, ?, ?)",
                "e1", "person:1", "person:2", "transfer", "{\"amount\":100}", "[\"confirmed\"]"
        );
        jdbcTemplate.update(
                "INSERT INTO edges (id, src, dst, kind, attrs, flags) VALUES (?, ?, ?, ?, ?, ?)",
                "e2", "person:2", "phone:+7999", "contact", "{}", "[]"
        );
    }

    @Test
    void oneHopReturnsFilteredEdges() throws Exception {
        String payload = """
            {
              "seeds": ["person:1"],
              "cursor": "",
              "limit": 10,
              "edgeKinds": ["transfer"]
            }
            """;

        String response = mockMvc.perform(post("/api/v1/graph/one-hop")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertThat(root.get("edges").size()).isEqualTo(1);
        assertThat(root.get("edges").get(0).get("kind").asText()).isEqualTo("transfer");

        Set<String> nodeIds = new HashSet<>();
        for (JsonNode node : root.get("nodes")) {
            nodeIds.add(node.get("id").asText());
        }
        assertThat(nodeIds).contains("person:1", "person:2");

        JsonNode page = root.get("pages").get(0);
        assertThat(page.get("seed").asText()).isEqualTo("person:1");
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        assertThat(page.get("endCursor").asText()).isEqualTo("person:2");
    }

    @Test
    void oneHopResolvesIdsWhenSeedsMissing() throws Exception {
        String payload = """
            {
              "ids": ["1"],
              "limit": 10,
              "edgeKinds": ["transfer"]
            }
            """;

        String response = mockMvc.perform(post("/api/v1/graph/one-hop")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertThat(root.get("edges").size()).isEqualTo(1);
        assertThat(root.get("edges").get(0).get("kind").asText()).isEqualTo("transfer");

        JsonNode page = root.get("pages").get(0);
        assertThat(page.get("seed").asText()).isEqualTo("person:1");
    }

    @Test
    void resolveAcceptsSnakeCaseAliases() throws Exception {
        String payload = """
            {
              "phone_no": ["+7999"],
              "party_rk": ["7700000000"]
            }
            """;

        String response = mockMvc.perform(post("/api/v1/graph/resolve")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        Set<String> nodeIds = new HashSet<>();
        for (JsonNode node : root.get("nodes")) {
            nodeIds.add(node.get("id").asText());
        }
        assertThat(nodeIds).contains("phone:+7999", "company:7700000000");
    }

    @Test
    void oneHopRequiresAtLeastOneSeed() throws Exception {
        String payload = """
            {
              "limit": 10
            }
            """;

        mockMvc.perform(post("/api/v1/graph/one-hop")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shortestPathReturnsEdgesAndLength() throws Exception {
        String payload = """
            {
              "from": "person:1",
              "to": "phone:+7999",
              "edgeKinds": ["transfer", "contact"],
              "maxHops": 6
            }
            """;

        String response = mockMvc.perform(post("/api/v1/graph/shortest-path")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        assertThat(root.get("length").asInt()).isEqualTo(2);
        assertThat(root.get("edges").size()).isEqualTo(2);
        assertThat(root.get("nodes").size()).isEqualTo(3);
    }
}
