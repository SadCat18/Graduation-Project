package com.skatehub.pojo.admin;

import lombok.Data;

import java.util.List;

@Data
public class CommentBatchDeleteRequest {

    private List<Long> commentIds;
}
