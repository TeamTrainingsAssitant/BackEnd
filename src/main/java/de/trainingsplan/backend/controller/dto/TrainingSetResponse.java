package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.TrainingSet;

public record TrainingSetResponse(Double weight, Integer reps, boolean completed) {

    public static TrainingSetResponse from(TrainingSet trainingSet) {
        return new TrainingSetResponse(trainingSet.getWeight(), trainingSet.getReps(), trainingSet.isCompleted());
    }
}
