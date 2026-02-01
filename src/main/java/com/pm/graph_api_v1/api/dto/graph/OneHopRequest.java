package com.pm.graph_api_v1.api.dto.graph;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record OneHopRequest(
        @Size(max = 200)
        List<String> seeds,

        @Size(max = 200)
        List<String> ids,

        @JsonAlias("phone_no")
        @Size(max = 200)
        List<String> phoneNos,

        @JsonAlias("party_rk")
        @Size(max = 200)
        List<String> partyRks,

        @Size(max = 200)
        String cursor,

        @Min(1) @Max(200)
        int limit,

        @Size(max = 20)
        Set<String> edgeKinds
) {
    @AssertTrue(message = "At least one of seeds/ids/phoneNos/partyRks must be provided")
    public boolean isAnySeedPresent() {
        return hasValues(seeds) || hasValues(ids) || hasValues(phoneNos) || hasValues(partyRks);
    }

    private static boolean hasValues(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
