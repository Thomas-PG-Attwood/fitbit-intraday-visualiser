package uk.co.ticklethepanda.health.activity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.co.ticklethepanda.health.ChartConfig;
import uk.co.ticklethepanda.utility.image.PngToByteArray;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityChartService {

    private static Logger log = LogManager.getLogger();

    private final ActivityService activityService;

    private byte[] dayImage;
    private byte[] dayByWeekdayImage;
    private byte[] dayByMonthImage;
    private byte[] dayImageSinceLastMonth;

    public ActivityChartService(@Autowired ActivityService activityService) {
        this.activityService = activityService;
    }

    @Scheduled(fixedRate = 1000 * 60, initialDelay = 1)
    public void cacheDayImage() throws IOException {
        log.info("caching activity chart");

        List<MinuteActivity> averageDays = activityService.getAverageDay();

        BufferedImage bufferedImage = createChart(averageDays);

        this.dayImage = PngToByteArray.convert(bufferedImage);

        log.info("cached activity chart");
    }

    @Scheduled(fixedRate = 1000 * 60, initialDelay = 1)
    public void cacheDayByRecent() throws IOException {
        log.info("caching activity by recent");
        List<MinuteActivity> activities = activityService.getAverageDayForRange(
                LocalDate.now().minus(30, ChronoUnit.DAYS),
                null);

        this.dayImageSinceLastMonth = PngToByteArray.convert(createChart(activities));
        log.info("cached activity by recent");
    }

    @Scheduled(fixedRate = 1000 * 60, initialDelay = 1)
    public void cacheDayByWeekdayImage() throws IOException {
        log.info("caching activity by weekday chart");
        Map<DayOfWeek, List<MinuteActivity>> entities = activityService.getAverageDayByWeekday();

        this.dayByWeekdayImage = PngToByteArray.convert(createFacetedChart(entities));
        log.info("caching activity by weekday chart");
    }

    @Scheduled(fixedRate = 1000 * 60, initialDelay = 1)
    public void cacheDayByMonthImage() throws IOException {
        log.info("caching activity by month chart");
        Map<Month, List<MinuteActivity>> entities = activityService.getAverageDayByMonth();

        this.dayByMonthImage = PngToByteArray.convert(createFacetedChart(entities));
        log.info("cached activity by month chart");
    }

    private BufferedImage createFacetedChart(Map<?, List<MinuteActivity>> entities) {
        LocalDate today = LocalDate.now();

        int chartWidth = 1000;
        int chartHeight = 150;

        BufferedImage imageSet = new BufferedImage(chartWidth, chartHeight * entities.size() + ChartConfig.AXIS_HEIGHT, BufferedImage.TYPE_INT_RGB);

        Graphics2D imageSetGraphics = imageSet.createGraphics();

        int i = 0;

        double max = entities.entrySet()
                .stream()
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.toList())
                .stream()
                .map(a -> a.getSteps())
                .reduce(Math::max)
                .get();

        for (Map.Entry<?, List<MinuteActivity>> entry : entities.entrySet()) {

            boolean last = entities.size() == i + 1;

            List<Date> xData = entry.getValue().stream()
                    .map(a -> Date.from(a.getTime().atDate(today).toInstant(ZoneOffset.UTC)))
                    .collect(Collectors.toList());
            List<Double> yData = entry.getValue().stream()
                    .map(a -> a.getSteps())
                    .collect(Collectors.toList());

            String title;
            if (entry.getKey() instanceof Month) {
                title = ((Month) entry.getKey()).getDisplayName(TextStyle.FULL, Locale.UK);
            } else if (entry.getKey() instanceof DayOfWeek) {
                title = ((DayOfWeek) entry.getKey()).getDisplayName(TextStyle.FULL, Locale.UK);
            } else {
                title = entry.getKey().toString();
            }

            XYChart chart = new XYChartBuilder()
                    .width(chartWidth)
                    .height(chartHeight + (last ? ChartConfig.AXIS_HEIGHT : 0))
                    .xAxisTitle(last ? "Time of Day" : null)
                    .yAxisTitle("Steps")
                    .title(title)
                    .theme(Styler.ChartTheme.GGPlot2)
                    .build();

            Font font = chart.getStyler().getAxisTickLabelsFont();

            chart.getStyler().setLegendVisible(false);
            chart.getStyler().setAxisTickLabelsFont(font.deriveFont(
                    Collections.singletonMap(
                            TextAttribute.WEIGHT, TextAttribute.WEIGHT_LIGHT)));
            chart.getStyler().setDatePattern("HH:mm");
            chart.getStyler().setYAxisMax(max);
            chart.getStyler().setYAxisTicksVisible(false);
            chart.getStyler().setXAxisTicksVisible(last);
            chart.getStyler().setChartPadding(ChartConfig.CHART_PADDING);

            XYSeries series = chart.addSeries("data", xData, yData);

            series.setMarker(SeriesMarkers.NONE);
            series.setLineColor(Color.getHSBColor((float) i / (float) entities.size(), 0.6f, 0.85f));

            BufferedImage individualImage = new BufferedImage(chartWidth, chartHeight + (last ? ChartConfig.AXIS_HEIGHT : 0), BufferedImage.TYPE_INT_RGB);

            Graphics2D individualGraphics = individualImage.createGraphics();
            chart.paint(individualGraphics, chart.getWidth(), chart.getHeight());
            individualGraphics.dispose();

            imageSetGraphics.drawImage(individualImage, 0, i * chartHeight, null);
            i++;
        }

        imageSetGraphics.dispose();
        return imageSet;
    }


    private BufferedImage createChart(List<MinuteActivity> averageDays) {
        LocalDate today = LocalDate.now();

        List<Double> yData = averageDays
                .stream()
                .map(a -> a.getSteps())
                .collect(Collectors.toList());

        List<Date> xData = averageDays.stream()
                .map(a -> Date.from(a.getTime().atDate(today).toInstant(ZoneOffset.UTC)))
                .collect(Collectors.toList());

        XYChart chart = new XYChartBuilder()
                .width(1000)
                .height(500)
                .xAxisTitle("Time of Day")
                .yAxisTitle("Steps")
                .theme(Styler.ChartTheme.GGPlot2)
                .build();

        Font font = chart.getStyler().getAxisTickLabelsFont();

        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setYAxisTicksVisible(false);
        chart.getStyler().setAxisTickLabelsFont(font.deriveFont(
                Collections.singletonMap(
                        TextAttribute.WEIGHT, TextAttribute.WEIGHT_LIGHT)));
        chart.getStyler().setDatePattern("HH:mm");
        chart.getStyler().setChartPadding(ChartConfig.CHART_PADDING);

        XYSeries series = chart.addSeries("data", xData, yData);

        series.setMarker(SeriesMarkers.NONE);

        BufferedImage bufferedImage = new BufferedImage(chart.getWidth(), chart.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = bufferedImage.createGraphics();
        chart.paint(graphics2D, chart.getWidth(), chart.getHeight());
        return bufferedImage;
    }


    public byte[] getAverageDayByMonthImage() throws IOException {
        if (dayByMonthImage == null) {
            cacheDayByMonthImage();
        }
        return dayByMonthImage;
    }

    public byte[] getAverageDayByWeekdayImage() throws IOException {
        if (dayByWeekdayImage == null) {
            cacheDayByWeekdayImage();
        }

        return dayByWeekdayImage;
    }

    public byte[] getAverageDayImage() throws IOException {
        if (dayImage == null) {
            cacheDayImage();
        }

        return dayImage;
    }

    public byte[] getAverageDayImageForLastMonth() throws IOException {
        if (dayImageSinceLastMonth == null) {
            cacheDayByRecent();
        }

        return dayImageSinceLastMonth;
    }

    public byte[] getAverageDayImageBetweenDates(LocalDate startDate, LocalDate endDate) throws IOException {
        log.info("caching activity by recent");
        List<MinuteActivity> activities = activityService.getAverageDayForRange(startDate, endDate);

        return PngToByteArray.convert(createChart(activities));
    }
}
