package risakka.raft.miscellanea;

import java.util.*;

public class SequentialContainer<E> {

    private static final int DEFAULT_INITIAL_CAPACITY = 25;
    private ArrayList<E> entries;

    SequentialContainer() {
        entries = new ArrayList<>(DEFAULT_INITIAL_CAPACITY);
    }

    public E get(int i) { // INDEX STARTS FROM 1
        if (i < 1 || i > entries.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        }
        return entries.get(i - 1);
    }

    public int size() {
        return entries.size();
    }

    void set(int i, E item) { // INDEX STARTS FROM 1
        if (i < 1 || i > entries.size() + 1) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        } else if (i <= entries.size()) {
            entries.set(i - 1, item);
        } else {  // i == entries.size() + 1
            entries.add(item);
        }
    }

    void deleteFrom(int i) {  // i is included
        if (i < 1 || i > entries.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        }
        int size = entries.size();
        for (int j = 0; j <= size - i; j++) {
            entries.remove(i - 1);
        }
    }

    public String toString() {
        return entries.toString();
    }
}
