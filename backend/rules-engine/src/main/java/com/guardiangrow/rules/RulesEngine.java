package com.guardiangrow.rules;

import java.time.Instant;

public final class RulesEngine {
  public RuleDecision canStartSession(String childId, String planItemId, Instant now) {
    // TODO: implement deterministic checks: schedule window, remaining daily limit, mandatory break.
    return new RuleDecision(true, "OK");
  }
}
