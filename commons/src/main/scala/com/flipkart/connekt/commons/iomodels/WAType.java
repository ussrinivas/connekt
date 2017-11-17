package com.flipkart.connekt.commons.iomodels;


public enum WAType {

  text("text"),
  image("image"),
  document("document"),
  audio("audio"),
  hsm("hsm");

  private final String value;

  /**
   * @param value
   */
  private WAType(final String value) {
    this.value = value;
  }

  /* (non-Javadoc)
   * @see java.lang.Enum#toString()
   */
  @Override
  public String toString() {
    return value;
  }

}
