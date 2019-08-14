package org.aion.unity;

/**
 * Immutable byte array wrapper.
 */
public class ByteArrayWrapper {

    private byte[] bytes;

    public ByteArrayWrapper(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    public byte[] toBytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ByteArrayWrapper that = (ByteArrayWrapper) o;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
