package com.example.restapi.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.restapi.dto.SubmitJobRequest;
import com.example.restapi.exception.ResourceNotFoundException;
import com.example.restapi.model.JobStatus;
import com.example.restapi.service.JobService;

class JobServiceTest {

	private JobService jobService;

	@BeforeEach
	void setUp() {
		jobService = new JobService(3);
	}

	@Test
	void submitCreatesQueuedJobAndReturnsJobId() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(10L);
		request.setPayload("hello");

		var response = jobService.submit(request);

		assertEquals(10L, response.jobId());
		assertEquals("hello", jobService.getStatus(10L).payload());
		assertEquals(JobStatus.QUEUED, jobService.getStatus(10L).status());
	}

	@Test
	void submitDuplicateJobIdThrows() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(11L);
		jobService.submit(request);

		assertThrows(IllegalArgumentException.class, () -> jobService.submit(request));
	}

	@Test
	void getStatusForUnknownJobThrowsNotFound() {
		assertThrows(ResourceNotFoundException.class, () -> jobService.getStatus(404L));
	}

	@Test
	void markRunningCompletedUpdatesStatus() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(12L);
		jobService.submit(request);

		jobService.markRunning(12L);
		assertEquals(JobStatus.RUNNING, jobService.getStatus(12L).status());

		jobService.markCompleted(12L);
		assertEquals(JobStatus.COMPLETED, jobService.getStatus(12L).status());
	}

	@Test
	void markFailedUpdatesStatusAndMessage() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(13L);
		jobService.submit(request);
		jobService.markRunning(13L);

		jobService.markFailed(13L, "processing error");

		assertEquals(JobStatus.FAILED, jobService.getStatus(13L).status());
		assertEquals("processing error", jobService.requireJob(13L).getErrorMessage());
	}

	@Test
	void canRetryRespectsMaxRetries() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(14L);
		jobService.submit(request);

		jobService.markRunning(14L);
		assertTrue(jobService.canRetry(14L));

		jobService.scheduleRetry(14L);
		jobService.markRunning(14L);
		assertTrue(jobService.canRetry(14L));

		jobService.scheduleRetry(14L);
		jobService.markRunning(14L);
		assertTrue(jobService.canRetry(14L));

		jobService.scheduleRetry(14L);
		jobService.markRunning(14L);
		assertFalse(jobService.canRetry(14L));
	}

	@Test
	void scheduleRetryRequeuesJobId() throws Exception {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(15L);
		jobService.submit(request);
		jobService.takeNextJobId();

		jobService.markRunning(15L);
		jobService.scheduleRetry(15L);

		assertEquals(JobStatus.QUEUED, jobService.getStatus(15L).status());
		assertEquals(15L, jobService.takeNextJobId());
	}

}
