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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.ArrayList;
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

    // Read-only fallback for rows created before trainingSets became canonical.
    @Column(insertable = false, updatable = false)
    private Double weight;

    @Column(insertable = false, updatable = false)
    private Integer sets;

    @Column(insertable = false, updatable = false)
    private Integer reps;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_exercise_training_sets", joinColumns = @JoinColumn(name = "plan_exercise_id"))
    @OrderColumn(name = "set_position")
    private List<TrainingSet> trainingSets = new ArrayList<>();

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
        if (weight == null && sets == null && reps == null) {
            this.trainingSets.clear();
            return;
        }
        int count = sets == null ? 1 : sets;
        List<TrainingSet> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add(new TrainingSet(weight, reps, false));
        updateTrainingSets(values);
    }

    /** Ersetzt die einzelnen Sätze und hält die bisherigen Summenfelder kompatibel. */
    public void updateTrainingSets(List<TrainingSet> trainingSets) {
        if (trainingSets == null || trainingSets.isEmpty()) {
            throw new IllegalArgumentException("At least one training set is required.");
        }
        this.trainingSets.clear();
        this.trainingSets.addAll(trainingSets);
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

    /** Setzt die Position beim Umsortieren der Übungen eines Plans. */
    public void moveTo(int position) {
        this.position = position;
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

    @Transient
    public Double getWeight() {
        return trainingSets.isEmpty() ? weight : trainingSets.get(0).getWeight();
    }

    @Transient
    public Integer getSets() {
        return trainingSets.isEmpty() ? sets : trainingSets.size();
    }

    @Transient
    public Integer getReps() {
        return trainingSets.isEmpty() ? reps : trainingSets.get(0).getReps();
    }

    public List<TrainingSet> getTrainingSets() {
        return trainingSets;
    }
}
