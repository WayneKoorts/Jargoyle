package com.jargoyle.service;

import com.jargoyle.service.properties.ChunkingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits extracted document text into retrieval-friendly chunks for the RAG pipeline.
 *
 * <p>The service works in two steps. It first looks for document structure such
 * as headings, numbered clauses, and separator lines so
 * chunks line up with meaningful sections where possible. If a section is still
 * too large for the target token budget, it falls back to sentence-based
 * splitting with overlap so neighbouring chunks preserve boundary context.
 *
 * <p>Token counts are estimated using a lightweight character-length heuristic
 * rather than a model-specific tokeniser. That keeps the chunking step fast and
 * deterministic while still being accurate enough for chunk sizing and later
 * prompt-budget decisions.
 */
@Service
public class ChunkingService {

    private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

    private static final Pattern NUMBERED_CLAUSE_PATTERN = Pattern.compile(
        "^(?:\\d+(?:\\.\\d+)*[.)]?|\\((?:[a-z]|[ivxlcdm]+)\\))\\s+.+$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR_LINE_PATTERN = Pattern.compile("^[-=_*]{3,}$");
    private static final Pattern SENTENCE_BOUNDARY_PATTERN = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z0-9])|\\n{2,}");

    /**
     * Matches characters that are typically PDF extraction artefacts rather than
     * meaningful content. These render as squares or invisible glyphs in UIs:
     * <ul>
     *   <li>C0 control characters (U+0000–U+001F) except tab, newline, carriage return</li>
     *   <li>C1 control characters (U+0080–U+009F)</li>
     *   <li>Private Use Area (U+E000–U+F8FF) — PDF fonts often map custom glyphs here</li>
     *   <li>Supplementary Private Use Areas (U+F0000–U+FFFFD, U+100000–U+10FFFD)</li>
     *   <li>Replacement character (U+FFFD) — emitted when a decoder can't map a byte</li>
     *   <li>Soft hyphens (U+00AD) — invisible layout hints that serve no purpose in plain text</li>
     * </ul>
     */
    private static final Pattern NON_RENDERABLE_PATTERN = Pattern.compile(
        "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x80-\\x9F\\uE000-\\uF8FF\\uFFFD\\u00AD]"
        + "|[\\uDB80-\\uDBFF][\\uDC00-\\uDFFD]"  // supplementary PUA (surrogate pairs)
    );

    private final ChunkingProperties properties;

    public ChunkingService(ChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * Breaks extracted document text into ordered chunks sized for retrieval.
     *
     * <p>The method normalises the input, attempts a section-aware split, merges
     * sections that are too small to stand on their own, and finally applies the
     * fallback splitter to any section that still exceeds the target token budget.
     *
     * @param extractedText full extracted document text, which may be null
     * @return chunk list in document order with sequential indexes and estimated token counts
     */
    public List<TextChunk> chunkText(String extractedText) {
        var normalisedText = normaliseText(extractedText);
        if (normalisedText.isBlank()) {
            return List.of();
        }

        var candidateSections = buildCandidateSections(normalisedText);
        var mergedSections = mergeUndersizedSections(candidateSections);

        var chunks = new ArrayList<TextChunk>();
        var chunkIndex = 0;
        for (String section : mergedSections) {
            for (String chunkContent : splitOversizedSection(section)) {
                var normalisedChunk = normaliseSection(chunkContent);
                if (normalisedChunk.isBlank()) {
                    continue;
                }

                chunks.add(new TextChunk(chunkIndex++, normalisedChunk, estimateTokenCount(normalisedChunk)));
            }
        }

        log.debug("Split extracted text into {} chunks", chunks.size());
        return chunks;
    }

    /**
     * Builds the first-pass section list by scanning for structural boundaries.
     *
     * <p>If no boundaries are detected, the original text is returned as a
     * single section so the fallback splitter can handle it later.
     */
    private List<String> buildCandidateSections(String text) {
        var lines = text.split("\\n", -1);
        var sections = new ArrayList<String>();
        var currentSection = new StringBuilder();
        var foundBoundary = false;

        for (int index = 0; index < lines.length; index++) {
            var line = stripTrailing(lines[index]);
            var trimmedLine = line.trim();

            if (isSeparatorLine(trimmedLine)) {
                foundBoundary = foundBoundary || currentSection.length() > 0;
                addSection(sections, currentSection);
                continue;
            }

            if (isSectionStart(lines, index) && currentSection.length() > 0) {
                foundBoundary = true;
                addSection(sections, currentSection);
            }

            if (currentSection.length() > 0) {
                currentSection.append('\n');
            }
            currentSection.append(line);
        }

        addSection(sections, currentSection);
        if (!foundBoundary) {
            return List.of(text);
        }

        return sections;
    }

    /**
     * Merges neighbouring sections when a section falls below the minimum size.
     *
     * <p>This keeps very small headings or short clauses from becoming noisy,
     * low-value retrieval chunks on their own.
     */
    private List<String> mergeUndersizedSections(List<String> sections) {
        if (sections.isEmpty()) {
            return List.of();
        }

        var mergedSections = new ArrayList<String>();
        for (int index = 0; index < sections.size(); index++) {
            var combinedSection = normaliseSection(sections.get(index));
            while (estimateTokenCount(combinedSection) < properties.minTokens() && index < sections.size() - 1) {
                index++;
                combinedSection = joinSections(combinedSection, sections.get(index));
            }

            if (!mergedSections.isEmpty() && estimateTokenCount(combinedSection) < properties.minTokens()) {
                var previousSection = mergedSections.removeLast();
                mergedSections.add(joinSections(previousSection, combinedSection));
            } else {
                mergedSections.add(combinedSection);
            }
        }

        return mergedSections;
    }

    /**
     * Splits a large section into target-sized chunks with overlap.
     *
     * <p>When the section already fits inside the token budget, it is returned
     * unchanged. Otherwise the method accumulates sentence-like units until the
     * target would be exceeded, then starts a new chunk with tail overlap.
     */
    private List<String> splitOversizedSection(String section) {
        var normalisedSection = normaliseSection(section);
        if (estimateTokenCount(normalisedSection) <= properties.targetTokens()) {
            return List.of(normalisedSection);
        }

        var chunks = new ArrayList<String>();
        var currentChunk = new StringBuilder();
        for (String unit : splitIntoUnits(normalisedSection)) {
            if (currentChunk.isEmpty()) {
                currentChunk.append(unit);
                continue;
            }

            var candidateChunk = joinSections(currentChunk.toString(), unit);
            if (estimateTokenCount(candidateChunk) <= properties.targetTokens()) {
                currentChunk.setLength(0);
                currentChunk.append(candidateChunk);
                continue;
            }

            chunks.add(normaliseSection(currentChunk.toString()));

            currentChunk.setLength(0);
            var overlap = extractOverlap(chunks.getLast());
            if (!overlap.isBlank()) {
                currentChunk.append(overlap);
                currentChunk.append("\n\n");
            }
            currentChunk.append(unit);
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(normaliseSection(currentChunk.toString()));
        }

        return chunks;
    }

    /**
     * Splits a section into sentence-like units before chunk assembly.
     *
     * <p>If one fragment is still too large on its own, the method delegates to
     * the approximate-length splitter so chunking can continue without relying on
     * perfectly-formed sentence punctuation.
     */
    private List<String> splitIntoUnits(String section) {
        var units = new ArrayList<String>();
        for (String fragment : SENTENCE_BOUNDARY_PATTERN.split(section)) {
            var trimmedFragment = normaliseSection(fragment);
            if (trimmedFragment.isBlank()) {
                continue;
            }

            if (estimateTokenCount(trimmedFragment) <= properties.targetTokens()) {
                units.add(trimmedFragment);
            } else {
                units.addAll(splitByApproximateLength(trimmedFragment));
            }
        }

        if (units.isEmpty()) {
            return List.of(section);
        }

        return units;
    }

    /**
     * Breaks a very large fragment into smaller pieces using a character budget.
     *
     * <p>This is the final fallback for unusually long lines or fragments where
     * sentence-based splitting does not produce manageable units.
     */
    private List<String> splitByApproximateLength(String text) {
        var pieces = new ArrayList<String>();
        var maxChars = tokensToCharacters(properties.targetTokens());
        var start = 0;

        while (start < text.length()) {
            var tentativeEnd = Math.min(text.length(), start + maxChars);
            var end = findPreferredBreak(text, start, tentativeEnd);
            var piece = normaliseSection(text.substring(start, end));
            if (!piece.isBlank()) {
                pieces.add(piece);
            }

            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }

        return pieces;
    }

    /**
     * Finds a whitespace break close to the target end point for a fallback split.
     *
     * <p>The search walks backwards so splits prefer word boundaries instead of
     * cutting through the middle of a word. If no suitable break is found, the
     * tentative end is used.
     */
    private int findPreferredBreak(String text, int start, int tentativeEnd) {
        if (tentativeEnd >= text.length()) {
            return text.length();
        }

        var minimumBreak = start + Math.max(1, tokensToCharacters(properties.targetTokens() / 2));
        for (int index = tentativeEnd; index > minimumBreak; index--) {
            if (Character.isWhitespace(text.charAt(index - 1))) {
                return index;
            }
        }

        return tentativeEnd;
    }

    /**
     * Determines whether the current line looks like the start of a new section.
     */
    private boolean isSectionStart(String[] lines, int index) {
        var trimmedLine = lines[index].trim();
        if (trimmedLine.isBlank()) {
            return false;
        }

        if (NUMBERED_CLAUSE_PATTERN.matcher(trimmedLine).matches()) {
            return true;
        }

        if (isAllCapsHeading(trimmedLine)) {
            return true;
        }

        return isShortHeading(trimmedLine, lines, index);
    }

    /**
     * Detects short headings written fully in uppercase.
     */
    private boolean isAllCapsHeading(String line) {
        if (line.length() > 80) {
            return false;
        }

        var hasLetters = false;
        for (char character : line.toCharArray()) {
            if (Character.isLetter(character)) {
                hasLetters = true;
                if (!Character.isUpperCase(character)) {
                    return false;
                }
            }
        }

        return hasLetters;
    }

    /**
     * Detects short heading-like lines surrounded by blank-line spacing.
     */
    private boolean isShortHeading(String line, String[] lines, int index) {
        if (line.length() > 80 || line.endsWith(".") || wordCount(line) > 12) {
            return false;
        }

        var nextLineBlank = index < lines.length - 1 && lines[index + 1].trim().isBlank();
        if (!nextLineBlank) {
            return false;
        }

        return index == 0 || lines[index - 1].trim().isBlank();
    }

    /**
     * Checks whether a line is a visual separator such as dashes or equals signs.
     */
    private boolean isSeparatorLine(String line) {
        return SEPARATOR_LINE_PATTERN.matcher(line).matches();
    }

    /**
     * Flushes the current section builder into the section list if it has content.
     */
    private void addSection(List<String> sections, StringBuilder sectionBuilder) {
        var section = normaliseSection(sectionBuilder.toString());
        if (!section.isBlank()) {
            sections.add(section);
        }
        sectionBuilder.setLength(0);
    }

    /**
     * Joins two section fragments with paragraph spacing and normalises the result.
     */
    private String joinSections(String first, String second) {
        return normaliseSection(first) + "\n\n" + normaliseSection(second);
    }

    /**
     * Extracts the tail portion of a chunk to reuse as overlap in the next chunk.
     */
    private String extractOverlap(String text) {
        var overlapCharacters = tokensToCharacters(properties.overlapTokens());
        if (text.length() <= overlapCharacters) {
            return text.trim();
        }

        var start = Math.max(0, text.length() - overlapCharacters);
        while (start < text.length() && !Character.isWhitespace(text.charAt(start))) {
            start++;
        }

        return text.substring(start).trim();
    }

    /**
     * Normalises extracted text ready for chunking.
     *
     * <p>Applies Unicode NFC normalisation to merge combining-mark sequences
     * (e.g. a base letter followed by a combining accent) into single composed
     * codepoints, then strips non-renderable characters that are common PDF
     * extraction artefacts, and finally normalises line endings.
     */
    private String normaliseText(String text) {
        if (text == null) {
            return "";
        }

        // NFC normalisation merges combining marks (e.g. e + ´ → é) into
        // composed codepoints so the text is consistent for downstream use.
        var normalised = Normalizer.normalize(text, Normalizer.Form.NFC);

        return NON_RENDERABLE_PATTERN.matcher(normalised).replaceAll("")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
    }

    /**
     * Normalises text and collapses repeated blank lines into paragraph spacing.
     */
    private String normaliseSection(String text) {
        return normaliseText(text).replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * Removes trailing whitespace from a line while preserving the line content.
     */
    private String stripTrailing(String line) {
        var end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1)) && line.charAt(end - 1) != '\n') {
            end--;
        }

        return line.substring(0, end);
    }

    /**
     * Estimates token count using a simple four-characters-per-token heuristic.
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    /**
     * Converts an approximate token count to a rough character budget.
     */
    private int tokensToCharacters(int tokenCount) {
        return Math.max(1, tokenCount * 4);
    }

    /**
     * Counts words in a heading candidate for the short-heading heuristic.
     */
    private int wordCount(String text) {
        if (text.isBlank()) {
            return 0;
        }

        return text.trim().split("\\s+").length;
    }

    public record TextChunk(
        int index,
        String content,
        int tokenCount
    ) { }
}