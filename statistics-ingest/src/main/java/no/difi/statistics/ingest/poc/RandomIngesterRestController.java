package no.difi.statistics.ingest.poc;

import no.difi.statistics.ingest.IngestService;
import no.difi.statistics.model.Measurement;
import no.difi.statistics.model.TimeSeriesPoint;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Random;

@RestController
public class RandomIngesterRestController {

    private IngestService service;

    public RandomIngesterRestController(IngestService service) {
        this.service = service;
    }

    @RequestMapping(
            method = RequestMethod.POST,
            value = "minutes/{owner}/{seriesName}",
            params = {"from", "to"},
            consumes = MediaType.APPLICATION_JSON_UTF8_VALUE
    )
    public void minutes(
            @PathVariable String owner,
            @PathVariable String seriesName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to
    ) {
        Random random = new Random();
        for (ZonedDateTime t = from; t.isBefore(to); t = t.plusMinutes(1)) {
            service.minute(
                    seriesName,
                    owner,
                    TimeSeriesPoint.builder().timestamp(t).measurement(new Measurement("count", random.nextLong())).build()
            );
        }

    }

}