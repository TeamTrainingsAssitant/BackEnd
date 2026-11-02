package de.trainingsplan.backend.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;

/** Ein einzelner Satz mit eigenen Trainingswerten. */
@Embeddable
public class TrainingSet {

    private Double weight;
    private Integer reps;
    // Kept mapped as false for compatibility with the existing non-null database column.
    @Column
    private boolean completed;

    protected TrainingSet() {
    }

    public TrainingSet(Double weight, Integer reps) {
        this.weight = weight;
        this.reps = reps;
    }

    public Double getWeight() { return weight; }

    public Integer getReps() { return reps; }
}
