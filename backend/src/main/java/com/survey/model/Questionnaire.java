package com.survey.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questionnaire")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Questionnaire {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private LocalDateTime deadline;

    @Column(nullable = false)
    private String status = "draft";

    @Column(name = "result_visibility", nullable = false)
    private String resultVisibility = "INSTANT_PUBLIC";

    @Column(name = "creator_token")
    private String creatorToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "questionnaire", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

    @Column(name = "cover_config", columnDefinition = "TEXT")
    private String coverConfig;

    @Transient
    private Integer responseCount;
}
