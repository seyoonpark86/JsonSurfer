package org.leo.json.parse;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.ParseException;
import org.leo.json.JsonPathBinder;
import org.leo.json.path.ArrayIndex;
import org.leo.json.path.JsonPath;
import org.leo.json.path.PathOperator;
import org.leo.json.path.PathOperator.Type;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;

public class ParsingContextImpl implements ParsingContext, JsonPathBinder, ContentHandler {

    private boolean stopped = false;
    private JsonPath currentPath;
    private Map<Integer, Map<JsonPath, JsonPathListener[]>> listenerMap = Maps.newHashMap();
    private ParsingObserver observer = new ParsingObserver();
    private JsonStructureFactory jsonStructureFactory;

    private interface CollectorProcessor {

        boolean process(JsonNodeCollector collector) throws IOException, ParseException;

    }

    private CollectorProcessor startJsonProcessor = new CollectorProcessor() {

        @Override
        public boolean process(JsonNodeCollector collector) throws IOException, ParseException {
            collector.startJSON();
            return true;
        }
    };

    public ParsingContextImpl(JsonStructureFactory jsonStructureFactory) {
        this.jsonStructureFactory = jsonStructureFactory;
    }

    @Override
    public void startJSON() throws ParseException, IOException {
        if (stopped) {
            return;
        }
        currentPath = JsonPath.buildPath();
        doMatching(null);
        observer.startJSON();
    }

    @Override
    public void endJSON() throws ParseException, IOException {
        if (stopped) {
            return;
        }
        observer.endJSON();
        currentPath.clear();
    }

    @Override
    public boolean startObject() throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        PathOperator top = currentPath.peek();
        if (top.getType() == Type.ARRAY) {
            ((ArrayIndex) top).increaseArrayIndex();
            doMatching(startJsonProcessor);
        }
        observer.startObject();
        return true;
    }

    private void doMatching(CollectorProcessor processor) throws IOException, ParseException {
        // TODO support only definite path
        int semiHashcode = semiHashcode(currentPath);
        Map<JsonPath, JsonPathListener[]> map = listenerMap.get(semiHashcode);
        if (map == null) {
            return;
        }
        LinkedList<JsonPathListener> listeners = null;
        for (Map.Entry<JsonPath, JsonPathListener[]> entry : map.entrySet()) {
            if (entry.getKey().match(currentPath)) {
                if (listeners == null) {
                    listeners = Lists.newLinkedList();
                }
                Collections.addAll(listeners, entry.getValue());
            }
        }
        if (listeners != null) {
            JsonNodeCollector collector = new JsonNodeCollector(listeners, this);
            collector.setFactory(jsonStructureFactory);
            if (processor != null) {
                processor.process(collector);
            }
            observer.addObserver(collector);
        }
    }

    @Override
    public boolean endObject() throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        observer.endObject();
        return true;
    }

    @Override
    public boolean startObjectEntry(String key) throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        currentPath.child(key);
        observer.startObjectEntry(key);
        doMatching(startJsonProcessor);
        return true;
    }

    @Override
    public boolean endObjectEntry() throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        currentPath.pop();
        observer.endObjectEntry();
        return true;
    }

    @Override
    public boolean startArray() throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        PathOperator top = currentPath.peek();
        if (top.getType() == Type.ARRAY) {
            ((ArrayIndex) top).increaseArrayIndex();
            doMatching(startJsonProcessor);
        }
        currentPath.array();
        observer.startArray();
        return true;
    }

    @Override
    public boolean endArray() throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        currentPath.pop();
        observer.endArray();
        return true;
    }

    @Override
    public boolean primitive(final Object value) throws ParseException, IOException {
        if (stopped) {
            return false;
        }
        PathOperator top = currentPath.peek();
        if (top.getType() == Type.ARRAY) {
            ((ArrayIndex) top).increaseArrayIndex();
            doMatching(startJsonProcessor);
        }
        observer.primitive(value);
        return true;
    }

    @Override
    public String getPath() {
        return this.currentPath.toString();
    }

    @Override
    public ContentHandler build() {
        this.listenerMap = Collections.unmodifiableMap(this.listenerMap);
        return this;
    }

    @Override
    public JsonPathBinder bind(JsonPath jsonPath, JsonPathListener... jsonPathListeners) {
        int semiHashcode = semiHashcode(jsonPath);
        Map<JsonPath, JsonPathListener[]> map = listenerMap.get(semiHashcode);
        if (map == null) {
            map = Maps.newHashMap();
            listenerMap.put(semiHashcode, map);
        }
        map.put(jsonPath, jsonPathListeners);
        return this;
    }

    @Override
    public void stopParsing() {
        this.stopped = true;
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }

    private int semiHashcode(JsonPath path) {
        return 31 * path.pathDepth() + path.peek().getType().ordinal();
    }
}
