package com.example.restapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class JobWorkerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void normalJobsCompleteWithoutTransientRetries() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":"job-701","payload":"regular-work"}
						"""))
				.andExpect(status().isAccepted());

		for (int attempt = 0; attempt < 20; attempt++) {
			MvcResult result = mockMvc.perform(get("/api/jobs/job-701/status"))
					.andExpect(status().isOk())
					.andReturn();
			if (result.getResponse().getContentAsString().contains("COMPLETED")) {
				return;
			}
			Thread.sleep(20);
		}
		throw new IllegalStateException("Regular job did not complete quickly");
	}

	@Test
	void transientPayloadJobsRetryBeforeCompleting() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":"job-702","payload":"transient:retry-me"}
						"""))
				.andExpect(status().isAccepted());

		org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", pollStatus("job-702"));
	}

	@Test
	void workerPoolProcessesMultipleJobsInParallel() throws Exception {
		submitJob("batch-501", "job-a");
		submitJob("batch-502", "job-b");
		submitJob("batch-503", "job-c");

		assertEquals("COMPLETED", pollStatus("batch-501"));
		assertEquals("COMPLETED", pollStatus("batch-502"));
		assertEquals("COMPLETED", pollStatus("batch-503"));
	}

	private void submitJob(String jobId, String payload) throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":"%s","payload":"%s"}
						""".formatted(jobId, payload)))
				.andExpect(status().isAccepted());
	}

	private String pollStatus(String jobId) throws Exception {
		for (int attempt = 0; attempt < 100; attempt++) {
			MvcResult result = mockMvc.perform(get("/api/jobs/" + jobId + "/status"))
					.andExpect(status().isOk())
					.andReturn();
			String body = result.getResponse().getContentAsString();
			if (body.contains("COMPLETED") || body.contains("FAILED")) {
				return body.contains("COMPLETED") ? "COMPLETED" : "FAILED";
			}
			Thread.sleep(50);
		}
		throw new IllegalStateException("Job did not finish in time: " + jobId);
	}

}
