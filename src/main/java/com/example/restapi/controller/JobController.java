package com.example.restapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.restapi.dto.JobStatusResponse;
import com.example.restapi.dto.SubmitJobRequest;
import com.example.restapi.dto.SubmitJobResponse;
import com.example.restapi.service.JobService;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

	private final JobService jobService;

	public JobController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping
	public ResponseEntity<SubmitJobResponse> submitJob(@Validated @RequestBody SubmitJobRequest request) {
		SubmitJobResponse response = jobService.submit(request);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	@GetMapping("/{jobId}/status")
	public JobStatusResponse getJobStatus(@PathVariable long jobId) {
		return jobService.getStatus(jobId);
	}

}
