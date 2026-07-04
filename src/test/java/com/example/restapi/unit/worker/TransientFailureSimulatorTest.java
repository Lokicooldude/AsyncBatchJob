package com.example.restapi.unit.worker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.restapi.exception.TransientJobException;
import com.example.restapi.model.Job;
import com.example.restapi.worker.TransientFailureSimulator;

class TransientFailureSimulatorTest {

	@Test
	void onlyTransientPayloadPrefixTriggersSimulation() {
		assertTrue(TransientFailureSimulator.shouldSimulateTransientFailure("transient:demo"));
		assertFalse(TransientFailureSimulator.shouldSimulateTransientFailure("demo"));
		assertFalse(TransientFailureSimulator.shouldSimulateTransientFailure(null));
	}

	@Test
	void normalPayloadNeverThrowsTransientError() {
		Job job = new Job(1L, "regular-work");
		job.beginProcessing();

		assertDoesNotThrow(() -> TransientFailureSimulator.maybeThrowTransientFailure(job, 2));
	}

	@Test
	void transientPayloadFailsUntilAttemptsExceedConfiguredFailures() {
		Job job = new Job(2L, "transient:retry-me");

		job.beginProcessing();
		assertThrows(
				TransientJobException.class,
				() -> TransientFailureSimulator.maybeThrowTransientFailure(job, 2));

		job.requeueForRetry();
		job.beginProcessing();
		assertThrows(
				TransientJobException.class,
				() -> TransientFailureSimulator.maybeThrowTransientFailure(job, 2));

		job.requeueForRetry();
		job.beginProcessing();
		assertDoesNotThrow(() -> TransientFailureSimulator.maybeThrowTransientFailure(job, 2));
	}

}
