package com.example.restapi.worker;

import com.example.restapi.exception.TransientJobException;
import com.example.restapi.model.Job;

public final class TransientFailureSimulator {

	public static final String TRANSIENT_PAYLOAD_PREFIX = "transient:";

	private TransientFailureSimulator() {
	}

	public static boolean shouldSimulateTransientFailure(String payload) {
		return payload != null && payload.startsWith(TRANSIENT_PAYLOAD_PREFIX);
	}

	public static void maybeThrowTransientFailure(Job job, int simulatedTransientFailures) {
		if (!shouldSimulateTransientFailure(job.getPayload())) {
			return;
		}
		if (job.getAttemptCount() <= simulatedTransientFailures) {
			throw new TransientJobException(
					"Simulated transient error on attempt " + job.getAttemptCount());
		}
	}

}
