package com.guardiangrow.rules;

public final class RuleDecision {
  public final boolean allowed;
  public final String reason;

  public RuleDecision(boolean allowed, String reason) {
    this.allowed = allowed;
    this.reason = reason;
  }
}
