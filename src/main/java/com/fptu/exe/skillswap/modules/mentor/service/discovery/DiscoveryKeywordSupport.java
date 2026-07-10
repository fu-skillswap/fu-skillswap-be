package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorAchievementRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorFeaturedProjectRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorSubjectResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryKeywordSupport {

    private final TagRepository tagRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorSubjectResultRepository mentorSubjectResultRepository;
    private final MentorFeaturedProjectRepository mentorFeaturedProjectRepository;
    private final MentorAchievementRepository mentorAchievementRepository;

    private volatile List<String> cachedKeywords = List.of();

    public String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    public List<String> tokenizeSearchText(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> token != null && !token.isBlank())
                .distinct()
                .toList();
    }

    public String toLikePattern(String value) {
        String normalized = normalizeSearchText(value);
        if (normalized.isBlank()) {
            return null;
        }
        return "%" + normalized + "%";
    }

    @jakarta.annotation.PostConstruct
    public void initCache() {
        refreshKeywordsCache();
    }

    @Scheduled(fixedRate = 300000)
    public void refreshKeywordsCache() {
        try {
            if (tagRepository == null || mentorServiceRepository == null) {
                return;
            }
            List<String> tags = tagRepository.findAll().stream()
                    .flatMap(tag -> Arrays.stream(new String[]{tag.getNameVi(), tag.getNameEn()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();

            List<String> serviceTitles = mentorServiceRepository.findAllActiveServiceTitles().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> subjects = mentorSubjectResultRepository.findAll().stream()
                    .flatMap(subject -> Arrays.stream(new String[]{subject.getSubjectCode(), subject.getSubjectName()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> projectTexts = mentorFeaturedProjectRepository.findAll().stream()
                    .flatMap(project -> Arrays.stream(new String[]{project.getTitle(), project.getContent(), project.getProjectDescription()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();
            List<String> achievementTexts = mentorAchievementRepository.findAll().stream()
                    .flatMap(achievement -> Arrays.stream(new String[]{achievement.getTitle(), achievement.getAwardDescription(), achievement.getProductHeader(), achievement.getProductDescription()}))
                    .filter(java.util.Objects::nonNull)
                    .map(this::normalizeSearchText)
                    .filter(s -> !s.isBlank())
                    .toList();

            List<String> merged = new ArrayList<>();
            merged.addAll(tags);
            merged.addAll(serviceTitles);
            merged.addAll(subjects);
            merged.addAll(projectTexts);
            merged.addAll(achievementTexts);
            List<String> deduplicated = List.copyOf(merged.stream().distinct().collect(Collectors.toList()));

            this.cachedKeywords = deduplicated;
            log.info("Refreshed search keywords cache: {} keywords", deduplicated.size());
        } catch (Exception ex) {
            log.warn("Failed to refresh search keywords cache", ex);
        }
    }

    public String correctSpelling(String normalizedKeyword) {
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return normalizedKeyword;
        }
        List<String> tokens = tokenizeSearchText(normalizedKeyword);
        if (tokens.isEmpty()) {
            return normalizedKeyword;
        }

        List<String> candidates = this.cachedKeywords;
        if (candidates.isEmpty()) {
            return normalizedKeyword;
        }

        List<String> correctedTokens = new ArrayList<>();
        boolean modified = false;

        for (String token : tokens) {
            if (token.length() > 30) {
                correctedTokens.add(token);
                continue;
            }

            String bestMatch = token;
            int bestDistance = Integer.MAX_VALUE;

            for (String candidate : candidates) {
                if (Math.abs(token.length() - candidate.length()) > 2) {
                    continue;
                }
                int distance = calculateLevenshteinDistance(token, candidate);
                int dynamicThreshold = Math.max(2, candidate.length() / 6);
                if (distance <= dynamicThreshold && distance < bestDistance) {
                    bestDistance = distance;
                    bestMatch = candidate;
                }
            }

            if (!bestMatch.equals(token)) {
                modified = true;
            }
            correctedTokens.add(bestMatch);
        }

        if (modified) {
            return String.join(" ", correctedTokens);
        }
        return normalizedKeyword;
    }

    private int calculateLevenshteinDistance(String s1, String s2) {
        int[] dp = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            dp[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int temp = dp[j];
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = Math.min(Math.min(dp[j - 1], dp[j]), prev) + 1;
                }
                prev = temp;
            }
        }
        return dp[s2.length()];
    }
}
