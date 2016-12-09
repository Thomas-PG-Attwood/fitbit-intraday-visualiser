package uk.co.ticklethepanda.fitbit.activity;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.apache.http.auth.Credentials;
import org.apache.logging.log4j.LogManager;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import uk.co.ticklethepanda.fitbit.caching.CacheLayerException;
import uk.co.ticklethepanda.fitbit.webapi.DaoException;
import uk.co.ticklethepanda.fitbit.webapi.FitbitApi;
import uk.co.ticklethepanda.utility.LocalDateRange;

public class IntradayActivityDaoWebApi implements IntradayActivityDao {

  private static class RateLimitStatus {

    private static long parseWaitTime(HttpResponse response) {
      return Long.parseLong(response.getHeaders().getRetryAfter()) * ONE_SECOND
          + ONE_SECOND;
    }

    @Expose
    private final int hourlyLimit;
    @Expose
    private final int remainingHits;

    @Expose
    private final LocalDate resetTime;

    private RateLimitStatus(int hourlyLimit, int remainingHits,
        LocalDate resetTime) {
      this.hourlyLimit = hourlyLimit;
      this.remainingHits = remainingHits;
      this.resetTime = resetTime;
    }

    public int getRemainingHits() {
      return remainingHits;
    }

    public boolean hasRemainingHits() {
      return remainingHits > 0;
    }
  }

  private static final Logger logger = LogManager.getLogger();

  private final static String ACTIVITIES_URL = FitbitApi.BASE_URL
      + "/user/-/activities/steps/date/%/1d.json";

  private final static String CLIENT_ACCESS_URL = FitbitApi.BASE_URL
      + "/account/clientAndViewerRateLimitStatus.json";

  private final static DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd").toFormatter();

  private static final int ONE_SECOND = 1000;

  private final IntradayActivityCacheLayer activityCache = new IntradayActivityCacheLayer();

  private final HttpRequestFactory requestFactory;

  public IntradayActivityDaoWebApi(HttpRequestFactory requestFactory) {
    this.requestFactory = requestFactory;

  }

  @Override
  public IntradayActivity getDayActivity(LocalDate date) throws DaoException {

    IntradayActivity value = null;
    try {
      logger.info("getting values for date " + date.toString() + " from cache.");
      value = activityCache.getValue(date);
    } catch (CacheLayerException e) {
      throw new DaoException("Could not day activity from cache", e);
    }

    if (value == null || !value.isFullDay()) {
      logger.info("getting values for date " + date.toString() + " from web.");
      value = retrieveOnlineIntradayData(date);
      try {
        logger.info("saving value for date " + date.toString() + " to cache.");
        activityCache.save(value);
      } catch (CacheLayerException e) {
        throw new DaoException("Could not save value to cache", e);
      }
    }
    return value;

  }

  @Override
  public IntradayActivityRange getIntradayActivityRange(LocalDate start, LocalDate end)
      throws DaoException {
    logger.info("getting values for dates" + start.toString() + " to " + end.toString());
    List<IntradayActivity> range = new ArrayList<IntradayActivity>();
    for (LocalDate date : new LocalDateRange(start, end)) {
      range.add(this.getDayActivity(date));
    }
    return new IntradayActivityRange(range);
  }

  public boolean isAvailable() {
    boolean available = false;

    GenericUrl url = new GenericUrl(CLIENT_ACCESS_URL);

    HttpRequest request = null;
    try {
      request = requestFactory.buildGetRequest(url);
    } catch (IOException e) {
      available = false;
    }

    RateLimitStatus status = null;
    try {
      
      status = GSON.fromJson(request.execute().parseAsString(), RateLimitStatus.class);
    } catch (IOException e) {
      available = false;
    }

    if (status.hasRemainingHits()) {
      available = true;
    }

    return available;
  }

  @Override
  public void saveDayActivity(IntradayActivity activity) throws DaoException {
    throw new DaoException("Cannot upload DayActivity to fitbit",
        new UnsupportedOperationException(
            "Cannot upload Day Activity to fitbit"));
  }

  
  /**
   * Checks if another response is required, if it is then it waits for it to be ready.
   * @param response
   * @return
   * @throws DaoException
   */
  private boolean isRetryRequired(HttpResponse response) throws DaoException {

    if (response.getHeaders().getRetryAfter() != null) {
      long waitTime = RateLimitStatus.parseWaitTime(response);
      try {
        Thread.sleep(waitTime);
      } catch (InterruptedException e) {
        throw new DaoException("Could not wait for response.", e);
      }
      return true;
    }
    return false;
  }

  private final static Gson GSON = new GsonBuilder()
      .excludeFieldsWithoutExposeAnnotation().create();

  private IntradayActivity retrieveOnlineIntradayData(LocalDate date) throws DaoException {
    GenericUrl url = new GenericUrl(ACTIVITIES_URL.replace("%", DATE_FORMATTER.format(date)));

    try {
      HttpResponse response;
      
      do {
        response = requestFactory.buildGetRequest(url).execute();
      } while (isRetryRequired(response));
      
      return GSON.fromJson(response.parseAsString(), IntradayActivity.class);
      
    } catch (IOException e) {
      throw new DaoException(e);
    }
  }
}
