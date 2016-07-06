package no.difi.statistics.ingest;

import no.difi.statistics.model.Measurement;
import no.difi.statistics.model.TimeSeriesPoint;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

abstract class AbstractIngester implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private Client client;

    AbstractIngester(Client client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("from")) throw new RuntimeException("Parameter \"from\" missing");
        if (!args.containsOption("to")) throw new RuntimeException("Parameter \"to\" missing");
        ZonedDateTime from = args.getOptionValues("from").stream().findFirst()
                .map(s -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s, ZonedDateTime::from))
                .orElseThrow(() -> new RuntimeException("Parameter \"from\" missing"));
        ZonedDateTime to = args.getOptionValues("to").stream().findFirst()
                .map(s -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s, ZonedDateTime::from))
                .orElseThrow(() -> new RuntimeException("Parameter \"to\" missing"));
        ingest(from, to);
    }

    protected abstract void ingest(ZonedDateTime from, ZonedDateTime to) throws IOException;

    void indexTimeSeriesPoint(String indexName, String indexType, TimeSeriesPoint dataPoint) throws IOException {
        byte[] document = document(dataPoint);
        logger.info(format("Ingesting: Index=%s Type=%s Point=%s", indexName, indexType, new String(document, "UTF-8")));
        if (indexType == null || indexType.trim().isEmpty()) {
            logger.warn("Ignoring point without type");
            return;
        }
        client.prepareIndex(indexName, indexType).setSource(document).get();
    }

    String indexNameForMinuteSeries(String baseName, ZonedDateTime timestamp) {
        return format(
                "%s:minute%s",
                baseName,
                DateTimeFormatter.ofPattern("yyyy.MM.dd").format(timestamp)
        );
    }

    private static byte[] document(TimeSeriesPoint dataPoint) {
        try {
             XContentBuilder builder = jsonBuilder().startObject()
                    .field("timestamp", formatTimestamp(dataPoint.getTimestamp()));
            for (Measurement measurement : dataPoint.getMeasurements()) {
                builder.field(measurement.getId(), measurement.getValue());
            }
            return builder.endObject().bytes().toBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String formatTimestamp(ZonedDateTime timestamp) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(timestamp);
    }

}