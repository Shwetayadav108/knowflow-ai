package com.knowflow.ai.ingestion;

public class ExtractedPage {
    private int pageNumber;
    private String content;
    private String sectionHeading;

    public ExtractedPage(int pageNumber, String content, String sectionHeading) {
        this.pageNumber = pageNumber;
        this.content = content;
        this.sectionHeading = sectionHeading;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSectionHeading() {
        return sectionHeading;
    }

    public void setSectionHeading(String sectionHeading) {
        this.sectionHeading = sectionHeading;
    }
}
