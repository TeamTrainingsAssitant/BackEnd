package de.trainingsplan.backend.controller.dto;

import de.trainingsplan.backend.model.TrainingSet;

public record TrainingSetResponse(Double weight, Integer reps) {

    public static TrainingSetResponse from(TrainingSet trainingSet) {
        return new TrainingSetResponse(trainingSet.getWeight(), trainingSet.getReps());
    }
}
