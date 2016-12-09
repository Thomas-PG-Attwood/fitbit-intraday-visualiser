package uk.co.ticklethepanda.fitbit.activity;

import com.google.api.client.http.HttpRequestFactory;

public class IntradayActivityDaoFactory {

  public static IntradayActivityDao getDao(HttpRequestFactory factory) {
    return new IntradayActivityDaoWebApi(factory);
  }

  private IntradayActivityDaoFactory() {
  }

}
