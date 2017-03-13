package risakka.server.util;

import java.util.*;

public class SequentialContainer<E> { // implements Iterable<E>, Collection<E>, List<E>, RandomAccess {

    private static final int DEFAULT_INITIAL_CAPACITY = 25;
    private ArrayList<E> entries;

    public SequentialContainer() {
        entries = new ArrayList<>(DEFAULT_INITIAL_CAPACITY);
    }

    public void set(int i, E item) { // INDEX STARTS FROM 1
        if (i < 1 || i > entries.size() + 1) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        } else if (i <= entries.size()) {
            entries.set(i - 1, item);
        } else {  // i == entries.size() + 1
            entries.add(item);
        }
    }

    public E get(int i) { // INDEX STARTS FROM 1
        if (i < 1 || i > entries.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        }
        return entries.get(i - 1);
    }

    public void deleteFrom(int i) {  // i is included
        if (i < 1 || i > entries.size()) {
            throw new ArrayIndexOutOfBoundsException("Invalid position for " + i);
        }
        int size = entries.size();
        for (int j = 0; j <= size - i; j++) {
            entries.remove(i - 1);
        }
    }

    public int size() {
        return entries.size();
    }

    public String toString() {
        return entries.toString();
    }
}
