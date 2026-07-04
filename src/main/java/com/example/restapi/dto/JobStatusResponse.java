package com.example.restapi.dto;

import com.example.restapi.model.JobStatus;

public record JobStatusResponse(String payload, JobStatus status) {
}
