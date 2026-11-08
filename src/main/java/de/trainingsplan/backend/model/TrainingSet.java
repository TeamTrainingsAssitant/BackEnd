package de.trainingsplan.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Ein einzelner Satz mit eigenen Trainingswerten. Gewicht und Wiederholungen sind Pflicht: Ein Satz ohne
 * Werte wird nicht gespeichert (Hibernate würde eine Zeile ohne Werte nicht wieder einlesen).
 */
@Embeddable
public class TrainingSet {

    @Column(nullable = false)
    private Double weight;

    @Column(nullable = false)
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
