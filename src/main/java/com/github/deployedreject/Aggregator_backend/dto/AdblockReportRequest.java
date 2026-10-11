package com.github.deployedreject.Aggregator_backend.dto;

import java.util.List;

public class AdblockReportRequest {
    private List<String> domains;

    public AdblockReportRequest() {
    }

    public AdblockReportRequest(List<String> domains) {
        this.domains = domains;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }
}
