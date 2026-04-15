package ma.ensa.aistudyassistant.model.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ma.ensa.aistudyassistant.model.enums.StudyType;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "study_sessions",
        indexes = {
                @Index(name = "idx_study_sessions_user", columnList = "user_id"),
                @Index(name = "idx_study_sessions_type", columnList = "type"),
                @Index(name = "idx_study_sessions_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull
    private User user;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false, length = 20)
    @ToString.Include
    private StudyType type;

    @Size(max = 120)
    @Column(length = 120)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String summaryContent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    @ToString.Include
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "studySession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "studySession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Flashcard> flashcards = new ArrayList<>();

    public void addQuestion(Question question) {
        questions.add(question);
        question.setStudySession(this);
    }

    public void removeQuestion(Question question) {
        questions.remove(question);
        question.setStudySession(null);
    }

    public void addFlashcard(Flashcard flashcard) {
        flashcards.add(flashcard);
        flashcard.setStudySession(this);
    }

    public void removeFlashcard(Flashcard flashcard) {
        flashcards.remove(flashcard);
        flashcard.setStudySession(null);
    }

    @PrePersist
    @PreUpdate
    public void normalizeFields() {
        if (subject != null) {
            subject = subject.trim();
            if (subject.isEmpty()) {
                subject = null;
            }
        }
    }
}