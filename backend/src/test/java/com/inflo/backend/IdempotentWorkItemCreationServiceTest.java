
package com.inflo.backend;

import com.inflo.backend.dto.CreateWorkItemRequest;
import com.inflo.backend.dto.WorkItemResponse;
import com.inflo.backend.entity.IdempotencyRecord;
import com.inflo.backend.entity.WorkItem;
import com.inflo.backend.repository.IdempotencyRecordRepository;
import com.inflo.backend.repository.WorkItemRepository;
import com.inflo.backend.service.IdempotentWorkItemCreationService;
import com.inflo.backend.service.WorkItemService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotentWorkItemCreationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private WorkItemRepository workItemRepository;

    @Mock
    private WorkItemService workItemService;

    @InjectMocks
    private IdempotentWorkItemCreationService service;

    private CreateWorkItemRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateWorkItemRequest();
        request.setTitle("Test work item");
        request.setDescription("Testing idempotency");
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(1L, " ", request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(jdbcTemplate, idempotencyRepository, workItemRepository, workItemService);
    }

    @Test
    void rejectsIdempotencyKeyLongerThan100Characters() {
        String key = "a".repeat(101);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(1L, key, request)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(jdbcTemplate, idempotencyRepository, workItemRepository, workItemService);
    }

    @Test
    void rejectsSameKeyWhenRequestDataIsDifferent() {
        when(jdbcTemplate.update(
                contains("INSERT INTO idempotency_records"),
                eq(1L), eq("request-123"), anyString()
        )).thenReturn(0);

        IdempotencyRecord existing = mock(IdempotencyRecord.class);
        when(existing.getRequestHash()).thenReturn("different-hash");

        when(idempotencyRepository.findByActorIdAndIdempotencyKey(1L, "request-123"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(1L, "request-123", request)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(workItemService, never()).createWorkItem(
                anyLong(), any(), anyString(), anyString(), any(), any(), any()
        );
    }

    @Test
    void returnsConflictWhenOriginalRequestHasNotCompleted() {
        when(jdbcTemplate.update(
                contains("INSERT INTO idempotency_records"),
                eq(1L), eq("request-456"), anyString()
        )).thenReturn(0);

        IdempotencyRecord existing = mock(IdempotencyRecord.class);
        when(existing.getRequestHash()).thenReturn(requestHashForTest(request));
        when(existing.getWorkItemId()).thenReturn(null);

        when(idempotencyRepository.findByActorIdAndIdempotencyKey(1L, "request-456"))
                .thenReturn(Optional.of(existing));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(1L, "request-456", request)
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    private String requestHashForTest(CreateWorkItemRequest request) {
        // Use the same SHA-256 input format as the production service.
        String data = String.join("|",
                String.valueOf(request.getTeam()),
                String.valueOf(request.getTitle()),
                String.valueOf(request.getDescription()),
                String.valueOf(request.getType()),
                String.valueOf(request.getPriority()),
                String.valueOf(request.getParentId())
        );

        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

