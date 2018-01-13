package com.flipkart.connekt.commons.iomodels;

public enum AttachmentType {

  image("image"),
  document("document");

  private final String value;

  /**
   * @param value
   */
  private AttachmentType(final String value) {
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
