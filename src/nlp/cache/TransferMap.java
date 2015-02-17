package nlp.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by keenon on 12/28/14.
 *
 * Concurrent map, which blocks on gets when nothing is present.
 */
public class TransferMap<K,V> {
    boolean finalized = false;

    Map<K,V> backingMap = new HashMap<>();
    Set<K> transfered = new HashSet<>();

    public V getBlocking(K key) {
        V value = backingMap.get(key);
        while (value == null) {
            synchronized (backingMap) {
                value = backingMap.get(key);
                if (value == null) {
                    if (finalized) {
                        return null;
                    }
                    if (transfered.contains(key)) {
                        throw new IllegalStateException("TransferMap removes elements on their first retrieval, and "+
                                "would be infinitely blocking right now if it hadn't thrown this generous exception :)");
                    }
                    try {
                        backingMap.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return value;
    }

    public V getBlockingAndRemove(K key) {
        V value = backingMap.get(key);
        while (value == null) {
            synchronized (backingMap) {
                value = backingMap.get(key);
                if (value == null) {
                    if (finalized) {
                        return null;
                    }
                    if (transfered.contains(key)) {
                        throw new IllegalStateException("TransferMap removes elements on their first retrieval, and "+
                        "would be infinitely blocking right now if it hadn't thrown this generous exception :)");
                    }
                    try {
                        backingMap.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    backingMap.remove(key);
                    transfered.add(key);
                }
            }
        }
        return value;
    }

    public void put(K key, V value) {
        synchronized (backingMap) {
            backingMap.put(key, value);
            backingMap.notifyAll();
        }
    }

    public void finish() {
        synchronized (backingMap) {
            finalized = true;
            backingMap.notifyAll();
        }
    }
}
