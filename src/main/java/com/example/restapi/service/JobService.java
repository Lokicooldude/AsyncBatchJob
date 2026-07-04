package com.example.restapi.service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.restapi.dto.JobStatusResponse;
import com.example.restapi.dto.SubmitJobRequest;
import com.example.restapi.dto.SubmitJobResponse;
import com.example.restapi.exception.ResourceNotFoundException;
import com.example.restapi.model.Job;
import com.example.restapi.model.JobStatus;

@Service
public class JobService {

	private final ConcurrentHashMap<Long, Job> jobs = new ConcurrentHashMap<>();
	private final BlockingQueue<Long> queue = new LinkedBlockingQueue<>();
	private final int maxRetries;

	public JobService(@Value("${job.processing.max-retries:3}") int maxRetries) {
		this.maxRetries = maxRetries;
	}

	public SubmitJobResponse submit(SubmitJobRequest request) {
		long jobId = request.getJobId();
		Job job = new Job(jobId, request.getPayload());
		Job existing = jobs.putIfAbsent(jobId, job);
		if (existing != null) {
			throw new IllegalArgumentException("Job already exists with id: " + jobId);
		}
		queue.offer(jobId);
		return new SubmitJobResponse(jobId);
	}

	public JobStatusResponse getStatus(long jobId) {
		Job job = requireJob(jobId);
		return new JobStatusResponse(job.getPayload(), job.getStatus());
	}

	public Long takeNextJobId() throws InterruptedException {
		return queue.take();
	}

	public Job requireJob(long jobId) {
		Job job = jobs.get(jobId);
		if (job == null) {
			throw new ResourceNotFoundException("Job not found with id: " + jobId);
		}
		return job;
	}

	public void markRunning(long jobId) {
		requireJob(jobId).beginProcessing();
	}

	public void markCompleted(long jobId) {
		requireJob(jobId).transitionToCompleted();
	}

	public void markFailed(long jobId, String errorMessage) {
		requireJob(jobId).transitionToFailed(errorMessage);
	}

	public boolean canRetry(long jobId) {
		return requireJob(jobId).getAttemptCount() <= maxRetries;
	}

	public void scheduleRetry(long jobId) {
		Job job = requireJob(jobId);
		job.requeueForRetry();
		queue.offer(jobId);
	}

}
