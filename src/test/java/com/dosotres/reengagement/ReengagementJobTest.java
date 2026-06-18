package com.dosotres.reengagement;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReengagementJobTest {

    @Mock
    private ReengagementService reengagementService;

    private ReengagementJob job;

    @BeforeEach
    void setUp() {
        job = new ReengagementJob(reengagementService);
    }

    @Test
    void delegatesToService() {
        job.run();

        verify(reengagementService).sendDueReengagements();
    }
}
