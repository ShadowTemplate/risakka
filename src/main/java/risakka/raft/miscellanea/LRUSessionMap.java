package risakka.raft.miscellanea;

import java.util.LinkedHashMap;
import java.util.Map;


public class LRUSessionMap<K, V> extends LinkedHashMap<K, V> {

    private final int sessionMapSize;

    public LRUSessionMap(int sessionMapSize) {
        super(10, 0.75f, true);
        this.sessionMapSize = sessionMapSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > sessionMapSize;
    }

}
