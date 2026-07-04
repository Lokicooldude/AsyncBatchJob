package com.example.restapi.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.restapi.dto.SubmitJobRequest;
import com.example.restapi.model.JobStatus;
import com.example.restapi.service.JobService;

class JobServiceConcurrencyTest {

	private JobService jobService;

	@BeforeEach
	void setUp() {
		jobService = new JobService(3);
	}

	@Test
	void concurrentSubmitWithSameJobIdOnlyAcceptsOne() throws Exception {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-1");
		request.setPayload("payload");

		ExecutorService executor = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger duplicateCount = new AtomicInteger();

		List<Future<?>> futures = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			futures.add(executor.submit(() -> {
				try {
					start.await();
					try {
						jobService.submit(request);
						successCount.incrementAndGet();
					}
					catch (IllegalArgumentException ex) {
						duplicateCount.incrementAndGet();
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}));
		}

		start.countDown();
		for (Future<?> future : futures) {
			future.get(5, TimeUnit.SECONDS);
		}
		executor.shutdown();

		assertEquals(1, successCount.get());
		assertEquals(19, duplicateCount.get());
		assertEquals(JobStatus.QUEUED, jobService.getStatus("job-1").status());
	}

	@Test
	void concurrentStatusReadsDuringWorkerTransitionsSeeValidStates() throws Exception {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-42");
		request.setPayload("concurrent-read");
		jobService.submit(request);

		Set<JobStatus> observedStatuses = ConcurrentHashMap.newKeySet();
		ExecutorService readers = Executors.newFixedThreadPool(8);
		CountDownLatch stopReading = new CountDownLatch(1);

		for (int i = 0; i < 8; i++) {
			readers.submit(() -> {
				while (stopReading.getCount() > 0) {
					observedStatuses.add(jobService.getStatus("job-42").status());
					Thread.onSpinWait();
				}
			});
		}

		jobService.markRunning("job-42");
		Thread.sleep(10);
		jobService.markCompleted("job-42");

		stopReading.countDown();
		readers.shutdown();
		assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS));

		assertTrue(observedStatuses.contains(JobStatus.QUEUED));
		assertTrue(observedStatuses.contains(JobStatus.RUNNING));
		assertTrue(observedStatuses.contains(JobStatus.COMPLETED));
		assertEquals(JobStatus.COMPLETED, jobService.getStatus("job-42").status());
	}

	@Test
	void invalidTransitionIsRejected() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-7");
		jobService.submit(request);

		assertThrows(IllegalStateException.class, () -> jobService.markCompleted("job-7"));
		assertEquals(JobStatus.QUEUED, jobService.getStatus("job-7").status());
	}

	@Test
	void scheduleRetryReturnsJobToQueuedState() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId("job-8");
		request.setPayload("retry-me");
		jobService.submit(request);

		jobService.markRunning("job-8");
		assertEquals(JobStatus.RUNNING, jobService.getStatus("job-8").status());
		assertTrue(jobService.canRetry("job-8"));

		jobService.scheduleRetry("job-8");
		assertEquals(JobStatus.QUEUED, jobService.getStatus("job-8").status());
	}

}
