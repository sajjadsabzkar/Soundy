package com.example.soundseeder;

import android.util.SparseArray;

import java.util.ArrayList;

/* Utility class for byte array pooling */
public abstract class ByteArrayPool {

    private static final SparseArray<ArrayList<byte[]>> byteArrayCache = new SparseArray<>(4);
    private static final int MAX_CACHE_SIZE = 8;

    /**
     * Retrieves a byte array from the pool if available, otherwise creates a new one.
     *
     * @param size the required size of the byte array
     * @return a byte array of the given size
     */
    public static byte[] getByteArray(int size) {
        synchronized (byteArrayCache) {
            ArrayList<byte[]> byteArrayList = byteArrayCache.get(size);
            if (byteArrayList == null || byteArrayList.isEmpty()) {
                return new byte[size];
            }
            return byteArrayList.remove(0);
        }
    }

    /**
     * Adds a byte array to the pool for future reuse if the cache size limit is not exceeded.
     *
     * @param byteArray the byte array to be added to the pool
     */
    public static void recycleByteArray(byte[] byteArray) {
        if (byteArray != null) {
            synchronized (byteArrayCache) {
                ArrayList<byte[]> byteArrayList = byteArrayCache.get(byteArray.length);
                if (byteArrayList == null) {
                    byteArrayList = new ArrayList<>();
                    byteArrayCache.put(byteArray.length, byteArrayList);
                }
                if (byteArrayList.size() < MAX_CACHE_SIZE) {
                    byteArrayList.add(byteArray);
                }
            }
        }
    }
}