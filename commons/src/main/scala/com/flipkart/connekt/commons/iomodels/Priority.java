package com.flipkart.connekt.commons.iomodels;


public enum Priority {

  HIGH("high"),
  NORMAL("normal");

  private final String text;

  /**
   * @param text
   */
  private Priority(final String text) {
    this.text = text;
  }

  /* (non-Javadoc)
   * @see java.lang.Enum#toString()
   */
  @Override
  public String toString() {
    return text;
  }
}
