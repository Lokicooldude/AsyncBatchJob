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
		request.setJobId("job-10");
		request.setPayload("hello");

		var response = jobService.submit(request);

		assertEquals("job-10", response.jobId());
		assertEquals("hello", jobService.getStatus("job-10").payload());
		assertEquals(JobStatus.QUEUED, jobService.getStatus("job-10").status());
	}

	@Test
	void submitDuplicateJobIdThrows() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-11");
		jobService.submit(request);

		assertThrows(IllegalArgumentException.class, () -> jobService.submit(request));
	}

	@Test
	void getStatusForUnknownJobThrowsNotFound() {
		assertThrows(ResourceNotFoundException.class, () -> jobService.getStatus("missing-job"));
	}

	@Test
	void markRunningCompletedUpdatesStatus() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-12");
		jobService.submit(request);

		jobService.markRunning("job-12");
		assertEquals(JobStatus.RUNNING, jobService.getStatus("job-12").status());

		jobService.markCompleted("job-12");
		assertEquals(JobStatus.COMPLETED, jobService.getStatus("job-12").status());
	}

	@Test
	void markFailedUpdatesStatusAndMessage() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-13");
		jobService.submit(request);
		jobService.markRunning("job-13");

		jobService.markFailed("job-13", "processing error");

		assertEquals(JobStatus.FAILED, jobService.getStatus("job-13").status());
		assertEquals("processing error", jobService.requireJob("job-13").getErrorMessage());
	}

	@Test
	void canRetryRespectsMaxRetries() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-14");
		jobService.submit(request);

		jobService.markRunning("job-14");
		assertTrue(jobService.canRetry("job-14"));

		jobService.scheduleRetry("job-14");
		jobService.markRunning("job-14");
		assertTrue(jobService.canRetry("job-14"));

		jobService.scheduleRetry("job-14");
		jobService.markRunning("job-14");
		assertTrue(jobService.canRetry("job-14"));

		jobService.scheduleRetry("job-14");
		jobService.markRunning("job-14");
		assertFalse(jobService.canRetry("job-14"));
	}

	@Test
	void scheduleRetryRequeuesJobId() throws Exception {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-15");
		jobService.submit(request);
		jobService.takeNextJobId();

		jobService.markRunning("job-15");
		jobService.scheduleRetry("job-15");

		assertEquals(JobStatus.QUEUED, jobService.getStatus("job-15").status());
		assertEquals("job-15", jobService.takeNextJobId());
	}

}
