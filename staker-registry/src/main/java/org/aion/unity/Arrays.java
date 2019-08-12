package org.aion.unity;

import org.aion.avm.userlib.AionList;

import java.util.List;

public class Arrays {
    public static int hashCode(byte[] a) {
        if (a == null)
            return 0;

        int result = 1;
        for (byte element : a)
            result = 31 * result + element;

        return result;
    }

    public static boolean equals(byte[] a, byte[] a2) {
        if (a == a2)
            return true;
        if (a == null || a2 == null)
            return false;

        int length = a.length;
        if (a2.length != length)
            return false;

        for (int i = 0; i < length; i++) {
            if (a[i] != a2[i]) {
                return false;
            }
        }

        return true;
    }

    public static byte[] copyOfRange(byte[] bytes, int i, int length) {
        byte[] ret = new byte[length];
        System.arraycopy(bytes, i, ret, 0, length);
        return ret;
    }

    public static byte[] copyOf(byte[] bytes, int i) {
        return copyOfRange(bytes, i, bytes.length - i);
    }

    public static <T> List<T> asList(T[] values) {
        AionList<T> list = new AionList();
        for (T v : values) {
            list.add(v);
        }
        return list;
    }
}
