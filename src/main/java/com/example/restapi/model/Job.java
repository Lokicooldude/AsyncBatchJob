package com.example.restapi.model;

import java.time.Instant;

public class Job {

	private final long id;
	private final String payload;
	private JobStatus status;
	private String errorMessage;
	private int attemptCount;
	private final Instant createdAt;
	private Instant updatedAt;

	public Job(long id, String payload) {
		this.id = id;
		this.payload = payload;
		this.status = JobStatus.QUEUED;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public long getId() {
		return id;
	}

	public String getPayload() {
		return payload;
	}

	public synchronized JobStatus getStatus() {
		return status;
	}

	public synchronized int getAttemptCount() {
		return attemptCount;
	}

	public synchronized Instant getUpdatedAt() {
		return updatedAt;
	}

	public synchronized String getErrorMessage() {
		return errorMessage;
	}

	public synchronized void beginProcessing() {
		attemptCount++;
		assertCurrentStatus(JobStatus.QUEUED, JobStatus.RUNNING);
	}

	public synchronized void transitionToCompleted() {
		assertCurrentStatus(JobStatus.RUNNING, JobStatus.COMPLETED);
	}

	public synchronized void requeueForRetry() {
		if (status != JobStatus.RUNNING) {
			throw new IllegalStateException(
					"Invalid job " + id + " transition: " + status + " -> " + JobStatus.QUEUED);
		}
		this.errorMessage = null;
		this.status = JobStatus.QUEUED;
		this.updatedAt = Instant.now();
	}

	public synchronized void transitionToFailed(String errorMessage) {
		if (status == JobStatus.COMPLETED || status == JobStatus.FAILED) {
			throw new IllegalStateException(
					"Cannot fail job " + id + " from status " + status);
		}
		this.errorMessage = errorMessage;
		this.status = JobStatus.FAILED;
		this.updatedAt = Instant.now();
	}

	private void assertCurrentStatus(JobStatus expected, JobStatus next) {
		if (status != expected) {
			throw new IllegalStateException(
					"Invalid job " + id + " transition: " + status + " -> " + next);
		}
		this.status = next;
		this.updatedAt = Instant.now();
	}

}
