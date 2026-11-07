package de.trainingsplan.backend.model;

import jakarta.persistence.Embeddable;

/** Ein einzelner Satz mit eigenen Trainingswerten. */
@Embeddable
public class TrainingSet {

    private Double weight;
    private Integer reps;

    protected TrainingSet() {
    }

    public TrainingSet(Double weight, Integer reps) {
        this.weight = weight;
        this.reps = reps;
    }

    public Double getWeight() { return weight; }

    public Integer getReps() { return reps; }
}
