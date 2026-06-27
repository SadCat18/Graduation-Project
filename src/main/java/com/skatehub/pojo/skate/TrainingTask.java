package com.skatehub.pojo.skate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tb_training_task")
public class TrainingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(nullable = false)
    private Long planId;

    private Integer dayNo;

    @Column(nullable = false, length = 100)
    private String taskTitle;

    @Column(columnDefinition = "text")
    private String taskContent;

    private Integer targetMinutes;

    @Column(length = 255)
    private String trickIds;

    private Integer sortOrder = 0;

    @PrePersist
    public void prePersist() {
        if (sortOrder == null) sortOrder = 0;
    }
}
