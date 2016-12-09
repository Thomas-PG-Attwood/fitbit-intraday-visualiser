package uk.co.ticklethepanda.fitbit.caching;

public class CacheLayerException extends Exception {

  private static final long serialVersionUID = -8535218881526870834L;

  public static CacheLayerException createLoadException(Throwable cause) {
    return new CacheLayerException("Could not load from cache", cause);
  }

  public static CacheLayerException createSaveException(Throwable cause) {
    return new CacheLayerException("Could not save to cache", cause);
  }

  private CacheLayerException() {
    super();
  }

  private CacheLayerException(String message) {
    super(message);
  }

  private CacheLayerException(String message, Throwable cause) {
    super(message, cause);
  }

  private CacheLayerException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  private CacheLayerException(Throwable cause) {
    super(cause);
  }

}
