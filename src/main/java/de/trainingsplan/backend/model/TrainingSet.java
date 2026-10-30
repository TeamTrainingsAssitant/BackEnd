package de.trainingsplan.backend.model;

import jakarta.persistence.Embeddable;

/** Ein einzelner Satz mit eigenen Trainingswerten und Abschlussstatus. */
@Embeddable
public class TrainingSet {

    private Double weight;
    private Integer reps;
    private boolean completed;

    protected TrainingSet() {
    }

    public TrainingSet(Double weight, Integer reps, boolean completed) {
        this.weight = weight;
        this.reps = reps;
        this.completed = completed;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer getReps() {
        return reps;
    }

    public boolean isCompleted() {
        return completed;
    }
}
