package de.trainingsplan.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;

/** Eine Übung in einem Plan. Name, gifUrl und Muskeln sind von ExerciseDB kopiert. */
@Entity
@Table(name = "plan_exercises")
public class PlanExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String gifUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_exercise_primary_muscles", joinColumns = @JoinColumn(name = "plan_exercise_id"))
    @Column(name = "muscle")
    private List<String> primaryMuscleGroups;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_exercise_secondary_muscles", joinColumns = @JoinColumn(name = "plan_exercise_id"))
    @Column(name = "muscle")
    private List<String> secondaryMuscleGroups;

    @Column(nullable = false)
    private int position;

    @Column(length = 500)
    private String note;

    /** Gewicht in kg, optional. */
    private Double weight;

    private Integer sets;

    private Integer reps;

    protected PlanExercise() {
    }

    public PlanExercise(Plan plan, String externalId, String name, String gifUrl,
            List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups,
            int position, String note) {
        this.plan = plan;
        this.externalId = externalId;
        this.name = name;
        this.gifUrl = gifUrl;
        this.primaryMuscleGroups = primaryMuscleGroups;
        this.secondaryMuscleGroups = secondaryMuscleGroups;
        this.position = position;
        this.note = note;
    }

    /** Ändert die Note, null entfernt sie. */
    public void updateNote(String note) {
        this.note = note;
    }

    /** Ersetzt Gewicht, Sätze und Wiederholungen, null entfernt den jeweiligen Wert. */
    public void updateTraining(Double weight, Integer sets, Integer reps) {
        this.weight = weight;
        this.sets = sets;
        this.reps = reps;
    }

    /** Ersetzt die ExerciseDB-Daten, ohne Position, Notiz oder Trainingswerte anzutasten. */
    public void replaceExerciseDetails(String externalId, String name, String gifUrl,
            List<String> primaryMuscleGroups, List<String> secondaryMuscleGroups) {
        this.externalId = externalId;
        this.name = name;
        this.gifUrl = gifUrl;
        this.primaryMuscleGroups = primaryMuscleGroups;
        this.secondaryMuscleGroups = secondaryMuscleGroups;
    }

    /** Rückt eine Position nach vorne, wenn davor eine Übung entfernt wurde. */
    public void moveUp() {
        this.position--;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public String getGifUrl() {
        return gifUrl;
    }

    public List<String> getPrimaryMuscleGroups() {
        return primaryMuscleGroups;
    }

    public List<String> getSecondaryMuscleGroups() {
        return secondaryMuscleGroups;
    }

    public int getPosition() {
        return position;
    }

    public String getNote() {
        return note;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer getSets() {
        return sets;
    }

    public Integer getReps() {
        return reps;
    }
}
