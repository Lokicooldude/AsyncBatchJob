package com.example.restapi.worker;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.example.restapi.exception.TransientJobException;
import com.example.restapi.model.Job;
import com.example.restapi.service.JobService;

import jakarta.annotation.PreDestroy;

@Component
public class JobWorker implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

	private final JobService jobService;
	private final long queuedDelayMs;
	private final long runningDelayMs;
	private final int simulatedTransientFailures;
	private final int workerCount;
	private final ExecutorService workerPool;
	private volatile boolean running = true;

	public JobWorker(
			JobService jobService,
			@Value("${job.processing.queued-delay-ms:2000}") long queuedDelayMs,
			@Value("${job.processing.running-delay-ms:3000}") long runningDelayMs,
			@Value("${job.processing.simulated-transient-failures:2}") int simulatedTransientFailures,
			@Value("${job.processing.worker-count:4}") int workerCount) {
		this.jobService = jobService;
		this.queuedDelayMs = queuedDelayMs;
		this.runningDelayMs = runningDelayMs;
		this.simulatedTransientFailures = simulatedTransientFailures;
		this.workerCount = workerCount;
		AtomicInteger threadIndex = new AtomicInteger();
		this.workerPool = Executors.newFixedThreadPool(workerCount, runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("job-worker-" + threadIndex.incrementAndGet());
			return thread;
		});
	}

	@Override
	public void run(ApplicationArguments args) {
		for (int i = 0; i < workerCount; i++) {
			workerPool.submit(this::processJobs);
		}
		log.info("Started job worker pool with {} workers", workerCount);
	}

	@PreDestroy
	void stop() {
		running = false;
		workerPool.shutdownNow();
		try {
			if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
				log.warn("Job worker pool did not terminate within timeout");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private void processJobs() {
		while (running) {
			try {
				String jobId = jobService.takeNextJobId();
				processJob(jobId);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void processJob(String jobId) throws InterruptedException {
		sleep(queuedDelayMs);

		jobService.markRunning(jobId);
		sleep(runningDelayMs);

		try {
			Job job = jobService.requireJob(jobId);
			processWithSimulation(job);
			jobService.markCompleted(jobId);
		}
		catch (TransientJobException ex) {
			handleTransientFailure(jobId, ex);
		}
		catch (Exception ex) {
			log.error("Failed to process job id={}", jobId, ex);
			jobService.markFailed(jobId, ex.getMessage());
		}
	}

	private void processWithSimulation(Job job) {
		TransientFailureSimulator.maybeThrowTransientFailure(job, simulatedTransientFailures);
		log.info(
				"Processed job id={} payload={} attempt={} thread={}",
				job.getId(),
				job.getPayload(),
				job.getAttemptCount(),
				Thread.currentThread().getName());
	}

	private void handleTransientFailure(String jobId, TransientJobException ex) {
		if (jobService.canRetry(jobId)) {
			log.warn(
					"Transient failure for job id={} on {}, scheduling retry: {}",
					jobId,
					Thread.currentThread().getName(),
					ex.getMessage());
			jobService.scheduleRetry(jobId);
			return;
		}
		log.error("Job id={} exceeded max retries after transient failures", jobId, ex);
		jobService.markFailed(jobId, ex.getMessage());
	}

	private void sleep(long delayMs) throws InterruptedException {
		if (delayMs > 0) {
			Thread.sleep(delayMs);
		}
	}

}
