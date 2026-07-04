package com.example.restapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SubmitJobRequest {

	@NotBlank
	@Size(max = 64)
	@Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must contain only letters, numbers, underscores, and hyphens")
	private String jobId;

	@Size(max = 1000)
	private String payload;

	public String getJobId() {
		return jobId;
	}

	public void setJobId(String jobId) {
		this.jobId = jobId;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

}
