package com.example.restapi.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class JobApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void submitJobReturnsJobId() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":101,"payload":"process-this"}
						"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value(101));
	}

	@Test
	void jobEventuallyCompletes() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":202,"payload":"async-work"}
						"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value(202));

		String finalStatus = pollStatus(202);
		org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", finalStatus);
	}

	@Test
	void getJobStatusReturnsPayloadAndStatus() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":404,"payload":"status-check"}
						"""))
				.andExpect(status().isAccepted());

		mockMvc.perform(get("/api/jobs/404/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payload").value("status-check"))
				.andExpect(jsonPath("$.status").value("QUEUED"))
				.andExpect(jsonPath("$.jobId").doesNotExist());
	}

	@Test
	void unknownJobReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/jobs/99999/status"))
				.andExpect(status().isNotFound());
	}

	@Test
	void duplicateJobIdReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":303,"payload":"first"}
						"""))
				.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":303,"payload":"duplicate"}
						"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void submitJobWithInvalidJobIdReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":0,"payload":"invalid"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.validationErrors.jobId").exists());
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
		throw new IllegalStateException("Job did not finish in time");
	}

}
