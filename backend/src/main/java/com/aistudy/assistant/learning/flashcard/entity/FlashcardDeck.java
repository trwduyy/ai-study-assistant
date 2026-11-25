package com.aistudy.assistant.learning.flashcard.entity;

import com.aistudy.assistant.common.entity.BaseEntity;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.learning.entity.Course;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Flashcard deck entity mapped to {@code flashcard_decks}.
 */
@Entity
@Table(name = "flashcard_decks")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardDeck extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "title", nullable = false, length = 255)
    private String title;
}
