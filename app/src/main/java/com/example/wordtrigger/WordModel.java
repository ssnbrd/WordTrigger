package com.example.wordtrigger;

public class WordModel {
    public String word, definition, synonyms;

    public WordModel() {}

    public WordModel(String word, String definition, String synonyms) {
        this.word = word;
        this.definition = definition;
        this.synonyms = synonyms;
    }
}
