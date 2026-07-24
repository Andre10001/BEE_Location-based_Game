package org.unicam.intermediate.models.dto;

import java.time.Instant;

public class ProcessStatusResponse {
    private String pid;
    private String status;
    private Instant endedAt;

    public ProcessStatusResponse(String pid, String status, Instant endedAt) {
        this.pid = pid;
        this.status = status;
        this.endedAt = endedAt;
    }

    public String getPid() {
        return pid;
    }

    public String getStatus() {
        return status;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
