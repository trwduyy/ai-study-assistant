package com.aistudy.assistant.ai.service;

import lombok.Builder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lightweight lexical retrieval helper selecting top-k relevant extracted chunks for grounded Q&A and explanations.
 */
@Component
public class ChunkGroundingService {

    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Set<String> STOPWORDS = Set.copyOf(List.of(
            "la", "là", "gì", "gi", "nào", "nao", "thế", "the", "này", "nay", "kia", "đó", "do",
            "của", "cua", "và", "va", "hay", "hoặc", "hoac", "nhưng", "nhung", "trong", "ngoài", "ngoai",
            "cho", "về", "ve", "ở", "o", "tại", "tai", "khi", "nếu", "neu", "thì", "thi", "với", "voi",
            "điểm", "diem", "khác", "khac", "nhau", "giữa", "giua", "những", "các", "cac",
            "một", "mot", "được", "duoc", "có", "co", "không", "khong", "sao", "như", "nhu",
            "bởi", "boi", "theo", "từ", "tu", "đến", "den", "bao", "gồm", "gom", "hãy",
            "giải", "giai", "thích", "thich", "trình", "trinh", "bày", "bay", "nêu",
            "what", "is", "are", "and", "or", "for", "with", "about", "how", "why", "in", "of", "to"
    ));

    public record ChunkCandidate(
            Long chunkId,
            Long materialId,
            String fileName,
            Integer pageFrom,
            Integer pageTo,
            String content,
            int chunkIndex
    ) {
    }

    @Builder
    public record ScoredChunk(
            ChunkCandidate chunk,
            int score
    ) {
    }

    public List<ChunkCandidate> selectTopChunks(String query, List<ChunkCandidate> candidates, int topK) {
        if (!StringUtils.hasText(query) || candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        List<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeText(query);

        return candidates.stream()
                .map(candidate -> new ScoredChunk(candidate, scoreCandidate(normalizedQuery, keywords, candidate.content())))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparingInt(s -> s.chunk().chunkIndex()))
                .limit(topK)
                .map(ScoredChunk::chunk)
                .toList();
    }

    public List<String> extractKeywords(String text) {
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return List.of();
        }

        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2 && !STOPWORDS.contains(token))
                .forEach(tokens::add);

        if (tokens.isEmpty()) {
            Arrays.stream(normalized.split("\\s+"))
                    .map(String::trim)
                    .filter(token -> token.length() >= 2)
                    .forEach(tokens::add);
        }
        return List.copyOf(tokens);
    }

    private int scoreCandidate(String normalizedQuery, List<String> keywords, String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        String normalizedContent = normalizeText(content);
        if (normalizedContent.isEmpty()) {
            return 0;
        }

        int distinctMatches = 0;
        int totalOccurrences = 0;
        for (String keyword : keywords) {
            int count = countSubstringOccurrences(normalizedContent, keyword);
            if (count > 0) {
                distinctMatches++;
                totalOccurrences += Math.min(count, 4);
            }
        }

        if (distinctMatches == 0) {
            return 0;
        }

        int score = (distinctMatches * 10) + totalOccurrences;
        if (normalizedQuery.length() >= 8 && normalizedContent.contains(normalizedQuery)) {
            score += 20;
        }
        return score;
    }

    private int countSubstringOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        return NON_WORD_PATTERN.matcher(input.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
