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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		"job.processing.simulated-transient-failures=5",
		"job.processing.max-retries=1",
		"job.processing.queued-delay-ms=10",
		"job.processing.running-delay-ms=10",
		"job.processing.worker-count=1"
})
class JobWorkerFailureIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void jobFailsPermanentlyWhenRetriesAreExhausted() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":601,"payload":"transient:will-fail"}
						"""))
				.andExpect(status().isAccepted());

		assertEquals("FAILED", pollStatus(601));
	}

	private String pollStatus(long jobId) throws Exception {
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
