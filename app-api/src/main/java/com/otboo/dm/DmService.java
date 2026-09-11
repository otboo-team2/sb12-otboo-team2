package com.otboo.dm;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.dm.dto.DirectMessageDto;
import com.otboo.dm.query.DirectMessageViewLoader;
import com.otboo.dm.repository.DirectMessageRepository;
import com.otboo.dm.util.DmKeyGenerator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmService {

    private static final String SORT_BY_CREATED_AT = "createdAt";

    private final DirectMessageRepository directMessageRepository;
    private final DirectMessageViewLoader viewLoader;

    public CursorResponse<DirectMessageDto> getMessages(UUID meId, UUID partnerId, CursorRequest request) {
        String dmKey = DmKeyGenerator.generate(meId, partnerId);

        List<DirectMessageDto> messages = viewLoader.loadSlice(dmKey, request);
        long totalCount = directMessageRepository.countByDmKey(dmKey);

        CursorRequest described = new CursorRequest(
            request.cursor(), request.idAfter(), request.limit(),
            SORT_BY_CREATED_AT, SortDirection.DESCENDING);

        return CursorResponse.of(
            messages, described, totalCount, DirectMessageDto::createdAt, DirectMessageDto::id);
    }
}
