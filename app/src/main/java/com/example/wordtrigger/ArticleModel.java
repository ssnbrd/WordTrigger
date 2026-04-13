package com.example.wordtrigger;

public class ArticleModel {
    public String title;
    public String content;

    public ArticleModel() {}

    public ArticleModel(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
}