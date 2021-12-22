package org.etimoloji.parser;

import zemberek.morphology.lexicon.DictionaryItem;

public class Entry implements Comparable<Entry> {
    public String word;
    protected int count;
    public String origin;

    public Entry(String word, int count) {
        this.word = sanitize(word);
        this.count = count;
    }

    public Entry(String word, String origin) {
        this.word = sanitize(word);
        this.origin = origin;
    }

    public Entry(int count, String origin) {
        this.count = count;
        this.origin = origin;
    }

    public Entry(String word, int count, String origin) {
        this.word = sanitize(word);
        this.count = count;
        this.origin = origin;
    }

    @Override
    public int compareTo(Entry other) {
        if (this.validOrigin() && !other.validOrigin()) {
            return 1;
        } else if (!this.validOrigin() && other.validOrigin()) {
            return -1;
        } else if (other.count > this.count) {
            return 1;
        } else if (this.count > other.count) {
            return -1;
        }
        return 0;
    }

    public static String sanitize(String word) {
        word = word.toLowerCase();
        word = word.replaceAll(" ", "");
        word = word.replaceAll("(?=.*)\\d(?=.*)", "");
        word = word.replaceAll("â", "a");
        word = word.replaceAll("ê", "e");
        word = word.replaceAll("î", "i");
        word = word.replaceAll("ô", "o");
        word = word.replaceAll("û", "u");

        return word;
    }

    public boolean validOrigin() {
        return this.origin == null || this.origin.length() > 6;
    }

    public static boolean validType(DictionaryItem item) {
        String type = item.primaryPos.getStringForm();
        return type.equals("Verb") || type.equals("Noun") || type.equals("Adj") || type.equals("Adv");
    }
}