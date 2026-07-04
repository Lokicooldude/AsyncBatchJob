package com.example.restapi.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.restapi.controller.JobController;
import com.example.restapi.dto.JobStatusResponse;
import com.example.restapi.dto.SubmitJobResponse;
import com.example.restapi.exception.GlobalExceptionHandler;
import com.example.restapi.exception.ResourceNotFoundException;
import com.example.restapi.model.JobStatus;
import com.example.restapi.service.JobService;

@WebMvcTest(JobController.class)
@Import(GlobalExceptionHandler.class)
class JobControllerWebTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JobService jobService;

	@Test
	void submitJobReturnsAcceptedWithJobId() throws Exception {
		when(jobService.submit(any())).thenReturn(new SubmitJobResponse(55L));

		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId":55,"payload":"unit-test"}
						"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value(55));

		verify(jobService).submit(any());
	}

	@Test
	void submitJobWithoutJobIdReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"payload":"missing-id"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.validationErrors.jobId").exists());
	}

	@Test
	void getJobStatusReturnsPayloadAndStatusOnly() throws Exception {
		when(jobService.getStatus(77L)).thenReturn(new JobStatusResponse("payload-77", JobStatus.RUNNING));

		mockMvc.perform(get("/api/jobs/77/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payload").value("payload-77"))
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andExpect(jsonPath("$.jobId").doesNotExist());
	}

	@Test
	void getJobStatusForUnknownJobReturnsNotFound() throws Exception {
		when(jobService.getStatus(88L)).thenThrow(new ResourceNotFoundException("Job not found with id: 88"));

		mockMvc.perform(get("/api/jobs/88/status"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Job not found with id: 88"));
	}

}
