package com.skatehub.pojo.message;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PrivateMessagePageResponse {

    private List<PrivateMessageView> list;
    private Long nextBeforeMessageId;
    private boolean hasMore;
}
