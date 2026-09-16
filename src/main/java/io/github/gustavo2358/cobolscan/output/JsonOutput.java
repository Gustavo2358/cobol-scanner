package io.github.gustavo2358.cobolscan.output;
import com.fasterxml.jackson.core.*;
import java.io.*;
import java.util.*;
public final class JsonOutput implements AutoCloseable {
    private final JsonGenerator json;
    public JsonOutput(OutputStream out) throws IOException {
        json = new JsonFactory().createGenerator(out); json.useDefaultPrettyPrinter();
        json.writeStartObject(); json.writeNumberField("schemaVersion", 1); json.writeArrayFieldStart("programs");
    }
    public void write(ScanResult r) throws IOException {
        json.writeStartObject(); json.writeStringField("source", r.source);
        array("programs", r.programs); array("externalFileNames", r.externalFileNames);
        array("tables", r.tables); array("copybooks", r.copybooks);
        array("sqlIncludes", r.sqlIncludes); array("dclgens", r.dclgens);
        json.writeBooleanField("programResolutionIncomplete", r.programResolutionIncomplete);
        json.writeStringField("scanStatus", r.scanStatus); json.writeEndObject();
    }
    private void array(String name, Collection<String> values) throws IOException {
        json.writeArrayFieldStart(name); for (String v : values) json.writeString(v); json.writeEndArray();
    }
    public void close() throws IOException { json.writeEndArray(); json.writeEndObject(); json.writeRaw('\n'); json.close(); }
}
