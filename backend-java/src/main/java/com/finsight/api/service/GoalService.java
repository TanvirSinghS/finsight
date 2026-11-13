package com.finsight.api.service;

import com.finsight.api.domain.Goal;
import com.finsight.api.domain.User;
import com.finsight.api.dto.GoalDtos.ContributionRequest;
import com.finsight.api.dto.GoalDtos.GoalRequest;
import com.finsight.api.dto.GoalDtos.GoalResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {
    private final GoalRepository goalRepository;
    private final UserService userService;

    public GoalService(GoalRepository goalRepository, UserService userService) {
        this.goalRepository = goalRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        return goalRepository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GoalResponse create(UUID userId, GoalRequest request) {
        User user = userService.get(userId);
        Goal goal = new Goal(user, request.name().trim(), request.targetAmount());
        goal.setCurrentAmount(request.currentAmount() == null ? BigDecimal.ZERO : request.currentAmount());
        goal.setTargetDate(request.targetDate());
        goal.setColor(request.color() == null ? "#6366f1" : request.color());
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, GoalRequest request) {
        Goal goal = findOwned(userId, id);
        goal.setName(request.name().trim());
        goal.setTargetAmount(request.targetAmount());
        if (request.currentAmount() != null) {
            goal.setCurrentAmount(request.currentAmount());
        }
        goal.setTargetDate(request.targetDate());
        if (request.color() != null) {
            goal.setColor(request.color());
        }
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse contribute(UUID userId, UUID id, ContributionRequest request) {
        Goal goal = findOwned(userId, id);
        goal.setCurrentAmount(goal.getCurrentAmount().add(request.amount()));
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        goalRepository.delete(findOwned(userId, id));
    }

    private Goal findOwned(UUID userId, UUID id) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Goal not found."));
    }

    private GoalResponse toResponse(Goal goal) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        int percent = target.signum() == 0 ? 100
                : current.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.HALF_UP).intValue();
        return new GoalResponse(goal.getId(), goal.getName(), target, current,
                target.subtract(current).max(BigDecimal.ZERO), Math.min(percent, 100),
                goal.getTargetDate(), goal.getColor(), percent >= 100);
    }
}
