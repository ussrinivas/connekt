package com.flipkart.connekt.commons.iomodels;


public enum WACallbackType {

  status("status"),
  reply("reply"),
  conversation("conversation");

  private final String value;

  /**
   * @param value
   */
  private WACallbackType(final String value) {
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
