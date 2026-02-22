package com.guardiangrow.reporting;

import java.util.List;

public final class WeeklyReport {
  public final String childId;
  public final String weekStart;
  public final int totalMinutes;
  public final List<String> highlights;

  public WeeklyReport(String childId, String weekStart, int totalMinutes, List<String> highlights) {
    this.childId = childId;
    this.weekStart = weekStart;
    this.totalMinutes = totalMinutes;
    this.highlights = highlights;
  }
}
