package com.knowflow.ai.analytics;

public class TimeSeriesPoint {
    private String date;
    private long count;

    public TimeSeriesPoint() {
    }

    public TimeSeriesPoint(String date, long count) {
        this.date = date;
        this.count = count;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
