package de.trainingsplan.backend.service;

import de.trainingsplan.backend.controller.dto.PlanExerciseRequest;
import de.trainingsplan.backend.controller.dto.PlanExerciseUpdateRequest;
import de.trainingsplan.backend.controller.dto.PlanRequest;
import de.trainingsplan.backend.exception.ApiException;
import de.trainingsplan.backend.model.Plan;
import de.trainingsplan.backend.model.PlanExercise;
import de.trainingsplan.backend.model.User;
import de.trainingsplan.backend.repository.PlanExerciseRepository;
import de.trainingsplan.backend.repository.PlanRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    private final PlanExerciseRepository planExerciseRepository;
    private final ExerciseDbClient exerciseDbClient;
    private final ExerciseCatalog exerciseCatalog;

    public PlanService(PlanRepository planRepository, PlanExerciseRepository planExerciseRepository,
            ExerciseDbClient exerciseDbClient, ExerciseCatalog exerciseCatalog) {
        this.planRepository = planRepository;
        this.planExerciseRepository = planExerciseRepository;
        this.exerciseDbClient = exerciseDbClient;
        this.exerciseCatalog = exerciseCatalog;
    }

    @Transactional(readOnly = true)
    public List<Plan> list(User user) {
        return planRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    @Transactional
    public Plan create(User user, PlanRequest request) {
        return planRepository.save(new Plan(user, request.name().trim(), request.description()));
    }

    @Transactional(readOnly = true)
    public Plan get(User user, Long planId) {
        return findOwnPlan(user, planId);
    }

    @Transactional(readOnly = true)
    public List<PlanExercise> listExercises(User user, Long planId) {
        findOwnPlan(user, planId);
        return planExerciseRepository.findByPlanIdOrderByPositionAsc(planId);
    }

    @Transactional(readOnly = true)
    public int exerciseCount(Long planId) {
        return planExerciseRepository.countByPlanId(planId);
    }

    @Transactional
    public Plan update(User user, Long planId, PlanRequest request) {
        Plan plan = findOwnPlan(user, planId);
        plan.update(request.name().trim(), request.description());
        return plan;
    }

    @Transactional
    public void delete(User user, Long planId) {
        planRepository.delete(findOwnPlan(user, planId));
    }

    /** Hängt eine Übung hinten an den Plan an. Name, gifUrl und Muskeln holt das Backend von ExerciseDB. */
    @Transactional
    public PlanExercise addExercise(User user, Long planId, PlanExerciseRequest request) {
        Plan plan = findOwnPlan(user, planId);
        ExerciseDbClient.ExternalExercise external = findExternalExercise(request.externalId().trim());
        int position = planExerciseRepository.countByPlanId(planId) + 1;
        PlanExercise exercise = new PlanExercise(plan, external.externalId(), external.name(), external.gifUrl(),
                external.primaryMuscleGroups(), external.secondaryMuscleGroups(), position, request.note());
        if (request.trainingSets() != null) {
            exercise.updateTrainingSets(request.trainingSets().stream().map(dto -> dto.toModel()).toList());
        } else {
            exercise.updateTraining(request.weight(), request.sets(), request.reps());
        }
        plan.touch();
        return planExerciseRepository.save(exercise);
    }

    /** Schaut zuerst im Katalog nach und fragt ExerciseDB nur, wenn die Übung dort nicht liegt. */
    private ExerciseDbClient.ExternalExercise findExternalExercise(String externalId) {
        ExerciseDbClient.ExternalExercise cached = exerciseCatalog.findById(externalId);
        if (cached != null) {
            return cached;
        }
        return exerciseDbClient.getExercise(externalId);
    }

    /** Ersetzt Note, Gewicht, Sätze und Wiederholungen einer Übung im Plan, null entfernt den Wert. */
    @Transactional
    public PlanExercise updateExercise(User user, Long planId, Long planExerciseId,
            PlanExerciseUpdateRequest request) {
        Plan plan = findOwnPlan(user, planId);
        PlanExercise exercise = planExerciseRepository.findByIdAndPlanId(planExerciseId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exercise not found."));
        exercise.updateNote(request.note());
        if (request.trainingSets() != null) {
            exercise.updateTrainingSets(request.trainingSets().stream().map(dto -> dto.toModel()).toList());
        } else if (request.weight() != null || request.sets() != null || request.reps() != null) {
            exercise.updateTraining(request.weight(), request.sets(), request.reps());
        }
        plan.touch();
        return exercise;
    }

    /** Findet passende Katalogübungen, die noch nicht im eigenen Plan stehen. */
    @Transactional(readOnly = true)
    public List<ExerciseDbClient.ExternalExercise> alternatives(User user, Long planId,
            Long planExerciseId, int limit) {
        findOwnPlan(user, planId);
        PlanExercise target = planExerciseRepository.findByIdAndPlanId(planExerciseId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exercise not found."));
        if (!exerciseCatalog.isLoaded()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ExerciseDB not reachable.");
        }

        ExerciseDbClient.ExternalExercise catalogTarget = exerciseCatalog.findById(target.getExternalId());
        Set<String> occupiedIds = new HashSet<>();
        planExerciseRepository.findByPlanIdOrderByPositionAsc(planId)
                .forEach(exercise -> occupiedIds.add(normalize(exercise.getExternalId())));

        return exerciseCatalog.getAll().stream()
                .filter(candidate -> !occupiedIds.contains(normalize(candidate.externalId())))
                .map(candidate -> new ScoredExercise(candidate, similarityScore(target, catalogTarget, candidate)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredExercise::score).reversed()
                        .thenComparing(scored -> scored.exercise().name(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(scored -> scored.exercise().externalId()))
                .limit(Math.max(1, Math.min(limit, 20)))
                .map(ScoredExercise::exercise)
                .toList();
    }

    /** Tauscht die Übungsdaten aus, behält aber Notiz und Trainingswerte der Planübung bei. */
    @Transactional
    public PlanExercise replaceExercise(User user, Long planId, Long planExerciseId, String externalId) {
        Plan plan = findOwnPlan(user, planId);
        PlanExercise target = planExerciseRepository.findByIdAndPlanId(planExerciseId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exercise not found."));
        String requestedId = externalId.trim();
        if (target.getExternalId().equalsIgnoreCase(requestedId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a different exercise.");
        }

        boolean alreadyInPlan = planExerciseRepository.findByPlanIdOrderByPositionAsc(planId).stream()
                .anyMatch(exercise -> !exercise.getId().equals(target.getId())
                        && exercise.getExternalId().equalsIgnoreCase(requestedId));
        if (alreadyInPlan) {
            throw new ApiException(HttpStatus.CONFLICT, "Exercise already in plan.");
        }

        ExerciseDbClient.ExternalExercise replacement = findExternalExercise(requestedId);
        target.replaceExerciseDetails(replacement.externalId(), replacement.name(), replacement.gifUrl(),
                replacement.primaryMuscleGroups(), replacement.secondaryMuscleGroups());
        plan.touch();
        return target;
    }

    private int similarityScore(PlanExercise target, ExerciseDbClient.ExternalExercise catalogTarget,
            ExerciseDbClient.ExternalExercise candidate) {
        Set<String> primary = normalized(target.getPrimaryMuscleGroups());
        Set<String> secondary = normalized(target.getSecondaryMuscleGroups());
        int score = overlapScore(candidate.primaryMuscleGroups(), primary, 4)
                + overlapScore(candidate.secondaryMuscleGroups(), primary, 3)
                + overlapScore(candidate.primaryMuscleGroups(), secondary, 2)
                + overlapScore(candidate.secondaryMuscleGroups(), secondary, 1);

        if (catalogTarget != null && overlaps(catalogTarget.bodyParts(), candidate.bodyParts())) {
            score++;
        }
        return score;
    }

    private int overlapScore(List<String> candidateGroups, Set<String> targetGroups, int points) {
        if (candidateGroups == null) return 0;
        return (int) candidateGroups.stream().map(PlanService::normalize)
                .filter(targetGroups::contains).distinct().count() * points;
    }

    private boolean overlaps(List<String> first, List<String> second) {
        Set<String> normalizedFirst = normalized(first);
        return second != null && second.stream().map(PlanService::normalize).anyMatch(normalizedFirst::contains);
    }

    private Set<String> normalized(List<String> values) {
        if (values == null) return Set.of();
        return values.stream().map(PlanService::normalize).collect(java.util.stream.Collectors.toSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredExercise(ExerciseDbClient.ExternalExercise exercise, int score) {
    }

    /** Entfernt eine Übung aus dem Plan, die übrigen rücken in der Position nach. */
    @Transactional
    public void deleteExercise(User user, Long planId, Long planExerciseId) {
        Plan plan = findOwnPlan(user, planId);
        PlanExercise exercise = planExerciseRepository.findByIdAndPlanId(planExerciseId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Exercise not found."));
        planExerciseRepository.findByPlanIdAndPositionGreaterThan(planId, exercise.getPosition())
                .forEach(PlanExercise::moveUp);
        planExerciseRepository.delete(exercise);
        plan.touch();
    }

    /** Setzt die Reihenfolge der Übungen neu, die IDs müssen genau die Übungen des Plans enthalten. */
    @Transactional
    public List<PlanExercise> reorderExercises(User user, Long planId, List<Long> exerciseIds) {
        Plan plan = findOwnPlan(user, planId);
        List<PlanExercise> current = planExerciseRepository.findByPlanIdOrderByPositionAsc(planId);

        Set<Long> requested = new HashSet<>(exerciseIds);
        Set<Long> existing = new HashSet<>();
        current.forEach(exercise -> existing.add(exercise.getId()));
        if (requested.size() != exerciseIds.size() || !requested.equals(existing)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Order must contain each exercise of the plan exactly once.");
        }

        Map<Long, PlanExercise> byId = new HashMap<>();
        current.forEach(exercise -> byId.put(exercise.getId(), exercise));
        List<PlanExercise> ordered = exerciseIds.stream().map(byId::get).toList();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i + 1);
        }
        plan.touch();
        return ordered;
    }

    /** Fremde und unbekannte Pläne verhalten sich gleich: 404. */
    private Plan findOwnPlan(User user, Long planId) {
        return planRepository.findByIdAndUserId(planId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Plan not found."));
    }
}
