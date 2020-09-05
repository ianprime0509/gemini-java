package xyz.ianjohnson.gemini.browser;

import xyz.ianjohnson.gemini.MimeType;

class UnsupportedMimeTypeException extends IllegalArgumentException {
  private final MimeType mimeType;

  public UnsupportedMimeTypeException(final MimeType mimeType) {
    super("Unsupported MIME type: " + mimeType);
    this.mimeType = mimeType;
  }

  public MimeType mimeType() {
    return mimeType;
  }
}
