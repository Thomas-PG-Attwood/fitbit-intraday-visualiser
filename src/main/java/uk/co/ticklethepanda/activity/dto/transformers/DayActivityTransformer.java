package uk.co.ticklethepanda.activity.dto.transformers;

import uk.co.ticklethepanda.activity.dto.DayActivity;
import uk.co.ticklethepanda.activity.dto.MinuteActivity;
import uk.co.ticklethepanda.activity.fitbit.FitbitIntradayActivity;

import java.util.TreeSet;

/**
 * @author Lovingly hand crafted by the ISIS Business Applications Team
 */
public class DayActivityTransformer implements Transformer<FitbitIntradayActivity, DayActivity> {

    @Override
    public DayActivity transform(FitbitIntradayActivity input) {
        TreeSet<MinuteActivity> activities = new TreeSet<>((a,b) -> a.time.compareTo(b.time));

        input.getIntradayMinuteActivitySeries().getElements().forEach(ima -> {
            MinuteActivity ma = new MinuteActivity(
                    ima.getTime(),
                    ima.getStepCount().intValue());

            activities.add(ma);
        });

        return new DayActivity(input.getDate(), activities);
    }
}
