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
		request.setJobId(1L);
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
		assertEquals(JobStatus.QUEUED, jobService.getStatus(1L).status());
	}

	@Test
	void concurrentStatusReadsDuringWorkerTransitionsSeeValidStates() throws Exception {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(42L);
		request.setPayload("concurrent-read");
		jobService.submit(request);

		Set<JobStatus> observedStatuses = ConcurrentHashMap.newKeySet();
		ExecutorService readers = Executors.newFixedThreadPool(8);
		CountDownLatch stopReading = new CountDownLatch(1);

		for (int i = 0; i < 8; i++) {
			readers.submit(() -> {
				while (stopReading.getCount() > 0) {
					observedStatuses.add(jobService.getStatus(42L).status());
					Thread.onSpinWait();
				}
			});
		}

		jobService.markRunning(42L);
		Thread.sleep(10);
		jobService.markCompleted(42L);

		stopReading.countDown();
		readers.shutdown();
		assertTrue(readers.awaitTermination(5, TimeUnit.SECONDS));

		assertTrue(observedStatuses.contains(JobStatus.QUEUED));
		assertTrue(observedStatuses.contains(JobStatus.RUNNING));
		assertTrue(observedStatuses.contains(JobStatus.COMPLETED));
		assertEquals(JobStatus.COMPLETED, jobService.getStatus(42L).status());
	}

	@Test
	void invalidTransitionIsRejected() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(7L);
		jobService.submit(request);

		assertThrows(IllegalStateException.class, () -> jobService.markCompleted(7L));
		assertEquals(JobStatus.QUEUED, jobService.getStatus(7L).status());
	}

	@Test
	void scheduleRetryReturnsJobToQueuedState() {
		SubmitJobRequest request = new SubmitJobRequest();
		request.setJobId(8L);
		request.setPayload("retry-me");
		jobService.submit(request);

		jobService.markRunning(8L);
		assertEquals(JobStatus.RUNNING, jobService.getStatus(8L).status());
		assertTrue(jobService.canRetry(8L));

		jobService.scheduleRetry(8L);
		assertEquals(JobStatus.QUEUED, jobService.getStatus(8L).status());
	}

}
