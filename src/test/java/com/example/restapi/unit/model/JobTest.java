package com.example.restapi.unit.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.example.restapi.model.Job;
import com.example.restapi.model.JobStatus;

class JobTest {

	@Test
	void newJobStartsQueuedWithZeroAttempts() {
		Job job = new Job("job-1", "payload");

		assertEquals("job-1", job.getId());
		assertEquals("payload", job.getPayload());
		assertEquals(JobStatus.QUEUED, job.getStatus());
		assertEquals(0, job.getAttemptCount());
		assertNull(job.getErrorMessage());
	}

	@Test
	void beginProcessingMovesQueuedToRunningAndIncrementsAttempt() {
		Job job = new Job("job-1", "payload");

		job.beginProcessing();

		assertEquals(JobStatus.RUNNING, job.getStatus());
		assertEquals(1, job.getAttemptCount());
	}

	@Test
	void transitionToCompletedMovesRunningToCompleted() {
		Job job = new Job("job-1", "payload");
		job.beginProcessing();

		job.transitionToCompleted();

		assertEquals(JobStatus.COMPLETED, job.getStatus());
	}

	@Test
	void requeueForRetryMovesRunningBackToQueued() {
		Job job = new Job("job-2", "payload");
		job.beginProcessing();

		job.requeueForRetry();

		assertEquals(JobStatus.QUEUED, job.getStatus());
		assertNull(job.getErrorMessage());
	}

	@Test
	void requeueForRetryRejectsNonRunningJob() {
		Job job = new Job("job-3", "payload");

		assertThrows(IllegalStateException.class, job::requeueForRetry);
	}

	@Test
	void transitionToFailedFromRunning() {
		Job job = new Job("job-1", "payload");
		job.beginProcessing();

		job.transitionToFailed("boom");

		assertEquals(JobStatus.FAILED, job.getStatus());
		assertEquals("boom", job.getErrorMessage());
	}

	@Test
	void invalidTransitionsAreRejected() {
		Job job = new Job("job-1", "payload");

		assertThrows(IllegalStateException.class, job::transitionToCompleted);
		assertThrows(IllegalStateException.class, job::requeueForRetry);

		job.beginProcessing();
		job.transitionToCompleted();

		assertThrows(IllegalStateException.class, job::beginProcessing);
		assertThrows(IllegalStateException.class, () -> job.transitionToFailed("fail"));
		assertThrows(IllegalStateException.class, job::requeueForRetry);
	}

}
