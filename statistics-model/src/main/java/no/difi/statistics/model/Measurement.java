package no.difi.statistics.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;

@JsonDeserialize(using = Measurement.MeasurementJsonDeserializer.class)
@XmlRootElement
public class Measurement {

    private String id;
    private long value;

    public Measurement(String id, long value) {
        if (id.startsWith("_")) throw new IllegalArgumentException("Invalid measurement id: cannot start with '_'");
        this.id = id;
        this.value = value;
    }

    @XmlElement
    public String getId() {
        return id;
    }

    @XmlElement
    public long getValue() {
        return value;
    }

    /**
     * Use custom deserializer to maintain immutability property
     */
    static class MeasurementJsonDeserializer extends JsonDeserializer<Measurement> {

        @Override
        public Measurement deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            return new Measurement(node.get("id").asText(), node.get("value").longValue());
        }

    }

    @Override
    public String toString() {
        return "Measurement{" +
                "id='" + id + '\'' +
                ", value=" + value +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Measurement that = (Measurement) o;

        if (value != that.value) return false;
        return id.equals(that.id);

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (int) (value ^ (value >>> 32));
        return result;
    }
}
