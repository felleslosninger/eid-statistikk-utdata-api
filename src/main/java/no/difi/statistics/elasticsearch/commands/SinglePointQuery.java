package no.difi.statistics.elasticsearch.commands;

import no.difi.statistics.model.TimeSeriesPoint;

public abstract class SinglePointQuery extends Query {

    public abstract TimeSeriesPoint execute();

}
