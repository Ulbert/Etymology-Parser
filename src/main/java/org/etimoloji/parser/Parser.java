package org.etimoloji.parser;

import java.io.*;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import zemberek.morphology.TurkishMorphology;
import zemberek.morphology.analysis.SentenceAnalysis;

public class Parser {
    final private TurkishMorphology morphology;
    private int added, total, relevantNotAdded;
    public String input;
    public Map<String, Entry> dictionary;
    public Map<String, Entry> parsedInput;
    public Map<String, Integer> parsedOrigin;

    public Parser(InputStream dictionary) throws IOException {
        morphology = TurkishMorphology.createWithDefaults();
        this.dictionary = loadEntries(dictionary);
    }

    public Parser(Path dictionary, Path input) throws IOException {
        morphology = TurkishMorphology.createWithDefaults();
        this.dictionary = loadEntries(dictionary);
        this.input = loadInput(input, false);
        this.parseMerge(true);
    }

    public Parser(Path dictionary, Path input, boolean inputIsDict) throws IOException {
        morphology = TurkishMorphology.createWithDefaults();
        this.dictionary = loadEntries(dictionary);
        this.input = loadInput(input, inputIsDict);
        this.parseMerge(true);
    }

    public void parseOrigin() {
        Map<String, Integer> parsedOrigin = new HashMap<>(32);
        Collection<Entry> words = this.parsedInput.values();

        words.forEach(entry -> parsedOrigin.merge(entry.origin, entry.count, Integer::sum));

        this.parsedOrigin = parsedOrigin;
    }

    public Map<String, Double> netDelta(Parser other) {
        if (this.parsedOrigin == null || this.parsedOrigin.size() == 0) {
            return null;
        } else if (other.parsedOrigin == null || other.parsedOrigin.size() == 0) {
            return null;
        }

        Map<String, Double> delta = new HashMap<>(32);
        this.parsedOrigin.forEach((origin, count) -> {
            if (other.parsedOrigin.containsKey(origin)) {
                delta.put(origin, (count / (double) added) * 100
                        - (other.parsedOrigin.get(origin) / (double) other.getAdded()) * 100);
            } else {
                delta.put(origin, (count / (double) added) * 100);
            }
        });

        return delta;
    }

    public Map<String, Double> percentDelta(Parser other) {
        if (this.parsedOrigin == null || this.parsedOrigin.size() == 0) {
            return null;
        } else if (other.parsedOrigin == null || other.parsedOrigin.size() == 0) {
            return null;
        }

        Map<String, Double> delta = new HashMap<>(32);
        this.parsedOrigin.forEach((origin, count) -> {
            if (other.parsedOrigin.containsKey(origin)) {
                delta.put(origin,
                        (count / (double) added) / (other.parsedOrigin.get(origin) / (double) other.getAdded()));
            } else {
                delta.put(origin, Double.POSITIVE_INFINITY);
            }
        });

        return delta;
    }

    public static int entryCount(Map<String, Entry> map) {
        return map.values().stream().mapToInt(entry -> entry.count).reduce(0, Integer::sum);
    }

    public static int originCount(Map<String, Integer> map) {
        return map.values().stream().reduce(0, Integer::sum);
    }

    public Map<String, Entry> loadEntries(InputStream input) throws IOException {
        try (Scanner scan = new Scanner(input).useDelimiter("([,\\n])")) {
            Map<String, Entry> map = new LinkedHashMap<>();

            scan.nextLine();
            if (!scan.hasNextLine()) {
                return null;
            }

            while (scan.hasNextLine()) {
                String word, origin;
                int count;

                word = scan.next();
                count = scan.nextInt();
                origin = scan.next();

                map.put(word, new Entry(word, count, origin));
            }

            return map;
        } catch (InputMismatchException e) {
            throw new IOException("Unexpected entry type encountered; database formatting may be incorrect.");
        }
    }

    public Map<String, Entry> loadEntries(Path input) throws IOException {
        try (Scanner scan = new Scanner(input).useDelimiter("([,\\n])")) {
            Map<String, Entry> map = new LinkedHashMap<>();

            scan.nextLine();
            if (!scan.hasNextLine()) {
                return null;
            }

            while (scan.hasNextLine()) {
                String word, origin;
                int count;

                word = scan.next();
                count = scan.nextInt();
                origin = scan.next();

                map.put(word, new Entry(word, count, origin));
            }

            return map;
        } catch (InputMismatchException e) {
            throw new IOException("Unexpected entry type encountered; database formatting may be incorrect.");
        }
    }

    public String loadInput(Path path, boolean dictionary) throws IOException {
        if (dictionary) {
            return String.join("\n", this.dictionary.keySet());
        }

        StringBuilder builder = new StringBuilder(1024);
        Files.lines(path).forEach(builder::append);

        return builder.toString();
    }

    public void saveToFile(Path saveLocation, boolean mergeEntries) throws IOException {
        Map<String, Entry> mergedMap = null;
        if (mergeEntries)
            mergedMap = mergeMaps(loadEntries(saveLocation));

        String header = "Entry," + "Count," + "Origin\n";
        Files.write(saveLocation, header.getBytes());

        if (mergeEntries) {
            for (Map.Entry<String, Entry> entry : mergedMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()) {
                String line = entry.getValue().word + "," + entry.getValue().count + "," + entry.getValue().origin
                        + "\n";
                Files.write(saveLocation, line.getBytes(), StandardOpenOption.APPEND);
            }
        } else {
            for (Map.Entry<String, Entry> entry : this.parsedInput.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue()).toList()) {
                String line = entry.getValue().word + "," + entry.getValue().count + "," + entry.getValue().origin
                        + "\n";
                Files.write(saveLocation, line.getBytes(), StandardOpenOption.APPEND);
            }
        }
    }

    public ArrayList<Entry> parseDistinct(String input, boolean strict) {
        final SentenceAnalysis result = morphology.analyzeAndDisambiguate(input);

        added = total = relevantNotAdded = 0;
        Map<String, Entry> parsedInput = new LinkedHashMap<>();
        ArrayList<Entry> parsedInputList = new ArrayList<>();
        result.bestAnalysis().forEach(word -> {
            if (!strict || Entry.validType(word.getDictionaryItem())) {
                String lemma = Entry.sanitize(word.getDictionaryItem().lemma);

                if (this.dictionary.get(lemma) != null) {
                    parsedInput.merge(lemma, new Entry(lemma, 1, this.dictionary.get(lemma).origin),
                            (entry1, entry2) -> new Entry(entry1.word, entry1.count + entry2.count, entry1.origin));
                    parsedInputList.add(new Entry(word.surfaceForm(), this.dictionary.get(lemma).origin.toLowerCase(Locale.ENGLISH)));
                    added++;
                } else if (!strict && !word.isUnknown()) {
                    parsedInputList.add(new Entry(word.surfaceForm(), "unk"));
                    relevantNotAdded++;
                }
            }
            total++;
        });

        this.parsedInput = parsedInput;
        return parsedInputList;
    }

    public void parseMerge(String input, boolean strict) {
        final SentenceAnalysis result = morphology.analyzeAndDisambiguate(input);

        added = total = relevantNotAdded = 0;
        Map<String, Entry> parsedInput = new LinkedHashMap<>();
        result.bestAnalysis().forEach(word -> {
            if (!strict || Entry.validType(word.getDictionaryItem())) {
                String lemma = Entry.sanitize(word.getDictionaryItem().lemma);

                if (this.dictionary.get(lemma) != null) {
                    parsedInput.merge(lemma, new Entry(lemma, 1, this.dictionary.get(lemma).origin),
                            (entry1, entry2) -> new Entry(entry1.word, entry1.count + entry2.count, entry1.origin));

                    added++;
                } else {
                    relevantNotAdded++;
                }
            }
            total++;
        });

        this.parsedInput = parsedInput;
    }

    private void parseMerge(boolean strict) {
        final SentenceAnalysis result = morphology.analyzeAndDisambiguate(this.input);

        added = total = relevantNotAdded = 0;
        Map<String, Entry> parsedInput = new LinkedHashMap<>();
        result.bestAnalysis().forEach(word -> {
            if (!strict || Entry.validType(word.getDictionaryItem())) {
                String lemma = Entry.sanitize(word.getDictionaryItem().lemma);

                if (this.dictionary.get(lemma) != null) {
                    parsedInput.merge(lemma, new Entry(lemma, 1, this.dictionary.get(lemma).origin),
                            (entry1, entry2) -> new Entry(entry1.word, entry1.count + entry2.count, entry1.origin));

                    added++;
                } else {
                    relevantNotAdded++;
                }
            }
            total++;
        });

        this.parsedInput = parsedInput;
    }

    public Map<String, Entry> mergeMaps(Map<String, Entry> other) {
        if (this.parsedInput.size() == 0) {
            return other;
        } else if (other == null || other.size() == 0) {
            return this.parsedInput;
        }

        Map<String, Entry> mergedMap = new LinkedHashMap<>(other);
        other.forEach(((word, entry) -> mergedMap.merge(word, entry,
                (entry1, entry2) -> new Entry(entry1.word, entry1.count + entry2.count, entry1.origin))));
        return mergedMap;
    }

    public int getAdded() {
        return added;
    }

    public int getTotal() {
        return total;
    }

    public int getRelevantNotAdded() {
        return relevantNotAdded;
    }

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("###.####");
        StringBuilder sb = new StringBuilder(total + " tokens parsed in input file.\n" +
                added + " words added, " + df.format((added / (double) total * 100)) + "% of total tokens.\n" +
                df.format((relevantNotAdded / (double) total) * 100) + "% of relevant words missed.\n");

        if (this.parsedOrigin != null) {
            this.parsedOrigin.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry -> {
                        // TODO: expand on analysis
                        sb.append(entry.getKey())
                                .append(": ")
                                .append(df.format((entry.getValue() / (double) added) * 100))
                                .append("%\n");
                    });
        }

        return sb.toString();
    }
}