package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * A utility class containing pre-defined, unaligned ValueLayout constants for common data types.
 *
 * <p>Using {@code .withByteAlignment(1)} on all multi-byte types allows these layouts to read from
 * or write to any byte offset, which is crucial for packed, zero-copy data structures.
 */
public final class Layouts {

    private Layouts() {}

    public static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfBoolean BOOLEAN = ValueLayout.JAVA_BOOLEAN;

    // Big Endian (Network Byte Order)

    public static final ValueLayout.OfShort SHORT_BE =
            (ValueLayout.OfShort)
                    ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfInt INT_BE =
            (ValueLayout.OfInt)
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfLong LONG_BE =
            (ValueLayout.OfLong)
                    ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfFloat FLOAT_BE =
            (ValueLayout.OfFloat)
                    ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfDouble DOUBLE_BE =
            (ValueLayout.OfDouble)
                    ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(1);

    // Little Endian

    public static final ValueLayout.OfShort SHORT_LE =
            (ValueLayout.OfShort)
                    ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfInt INT_LE =
            (ValueLayout.OfInt)
                    ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfLong LONG_LE =
            (ValueLayout.OfLong)
                    ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfFloat FLOAT_LE =
            (ValueLayout.OfFloat)
                    ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
    public static final ValueLayout.OfDouble DOUBLE_LE =
            (ValueLayout.OfDouble)
                    ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN).withByteAlignment(1);
}
