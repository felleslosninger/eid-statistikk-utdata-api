package no.difi.statistics.ingest.poc;

import no.difi.statistics.ingest.IngestService;
import no.difi.statistics.model.Measurement;
import no.difi.statistics.model.TimeSeriesPoint;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.time.ZonedDateTime;
import java.util.Random;

public class RandomIngester implements ApplicationRunner {

    private IngestService service;

    public RandomIngester(IngestService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        if (!applicationArguments.containsOption("from")) return;
        ApplicationArgumentsReader argumentsReader = new ApplicationArgumentsReader(applicationArguments);
        Random random = new Random();
        for (ZonedDateTime t = argumentsReader.from(); t.isBefore(argumentsReader.to()); t = t.plusMinutes(1)) {
            long value = random.nextLong();
            service.minute(
                    "random",
                    "randomOrganizationNumber",
                    TimeSeriesPoint.builder().timestamp(t).measurement(new Measurement("count", value)).build()
            );
        }
    }

}