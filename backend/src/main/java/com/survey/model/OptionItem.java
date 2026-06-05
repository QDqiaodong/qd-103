package com.survey.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "option_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionItem {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}
