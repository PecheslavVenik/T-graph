package com.pm.graph_api_v1.api.dto.graph;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ResolveRequest(
        @Size(max = 200)
        List<String> ids,

        @JsonAlias("phone_no")
        @Size(max = 200)
        List<String> phoneNos,

        @JsonAlias("party_rk")
        @Size(max = 200)
        List<String> partyRks
) {}
