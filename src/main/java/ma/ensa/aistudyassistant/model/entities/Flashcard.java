package ma.ensa.aistudyassistant.model.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(
        name = "flashcards",
        indexes = {
                @Index(name = "idx_flashcards_session", columnList = "study_session_id"),
                @Index(name = "idx_flashcards_order", columnList = "order_index")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_session_id", nullable = false)
    @NotNull
    private StudySession studySession;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    @ToString.Include
    private String front;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String back;

    @Min(0)
    @Column(name = "order_index")
    private Integer orderIndex;
}