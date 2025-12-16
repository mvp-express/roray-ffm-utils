package express.mvp.roray.ffm.utils.memory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A reusable, zero-allocation flyweight for viewing a UTF-8 encoded slice of a MemorySegment as a
 * String.
 *
 * <p>This object does NOT copy the string data. It holds a reference to the underlying segment.
 * The {@link #toString()} method is the only one that allocates a new String on the heap and
 * should only be used for debugging or moving data off the critical path.
 */
@SuppressWarnings("checkstyle:NeedBraces") // Single-line returns used for performance-critical code
public final class Utf8View {
    private MemorySegment segment;
    private long offset;
    private int length; // length in bytes

    /**
     * Wraps the target segment slice at the specified offset & length. This makes the Utf8View
     * object point to the wrapped data in a flyweight read pattern.
     */
    public void wrap(MemorySegment segment, long offset, int length) {
        this.segment = segment;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Returns the underlying MemorySegment this Utf8View object is wrapping.
     *
     * @return The MemorySegment, or null if not wrapped.
     */
    public MemorySegment segment() {
        return segment;
    }

    /**
     * Returns the offset within the segment where the UTF-8 data starts.
     *
     * @return The byte offset.
     */
    public long offset() {
        return offset;
    }

    /**
     * Returns the length of the UTF-8 data in bytes.
     *
     * @return The byte length.
     */
    public long byteSize() {
        return length;
    }

    /**
     * Checks if this view has been wrapped around valid data.
     *
     * @return true if wrapped with a non-null segment, false otherwise.
     */
    public boolean isValid() {
        return segment != null;
    }

    /**
     * The ONLY method that allocates a heap object. Use this only when you need to convert the view
     * to a standard Java String (e.g., for logging). It will serialize the UTF-8 bytes into a new
     * String. Hence, use sparingly on performance-critical paths.
     *
     * @return A new String object containing the data.
     */
    @Override
    public String toString() {
        if (segment == null || length == 0) {
            return "";
        }
        //        byte[] bytes = segment.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE);
        //        return new String(bytes, StandardCharsets.UTF_8);

        ByteBuffer bb =
                segment.asSlice(offset, length)
                        .asByteBuffer(); // view with position=0, limit=length
        return StandardCharsets.UTF_8.decode(bb).toString();
    }

    /**
     * A zero-allocation method to compare the view's content with a Java String. This method
     * performs byte-by-byte UTF-8 comparison without allocating any heap objects, making it
     * suitable for high-frequency trading and other zero-GC scenarios.
     *
     * @param other The String to compare against.
     * @return true if the content is identical, false otherwise.
     * @throws IllegalStateException if this view is not valid (not wrapped).
     */
    public boolean equalsString(String other) {
        if (other == null) return false;

        // Edge case: view not wrapped
        if (segment == null) return other.isEmpty();

        // Edge case: both empty
        if (length == 0) return other.isEmpty();

        // Edge case: other empty but we have data
        if (other.isEmpty()) return length == 0;

        // Quick length check: encode the string and compare byte lengths
        int expectedByteLength = calculateUtf8ByteLength(other);
        if (expectedByteLength != length) return false;

        // Byte-by-byte comparison with on-the-fly UTF-8 decoding
        long pos = offset;
        long endPos = offset + length;

        for (int i = 0; i < other.length(); i++) {
            // Edge case: ran out of bytes in segment
            if (pos >= endPos) return false;

            char expected = other.charAt(i);
            byte b1 = segment.get(Layouts.BYTE, pos++);

            if ((b1 & 0x80) == 0) {
                // 1-byte ASCII character
                if (expected != (char) b1) return false;
            } else if ((b1 & 0xE0) == 0xC0) {
                // 2-byte character
                if (pos >= endPos) return false; // Edge case: incomplete sequence
                byte b2 = segment.get(Layouts.BYTE, pos++);

                // Validate continuation byte
                if ((b2 & 0xC0) != 0x80) return false; // Invalid UTF-8

                char actual = (char) (((b1 & 0x1F) << 6) | (b2 & 0x3F));
                if (expected != actual) return false;
            } else if ((b1 & 0xF0) == 0xE0) {
                // 3-byte character
                if (pos + 1 >= endPos) return false; // Edge case: incomplete sequence
                byte b2 = segment.get(Layouts.BYTE, pos++);
                byte b3 = segment.get(Layouts.BYTE, pos++);

                // Validate continuation bytes
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) return false; // Invalid UTF-8

                char actual = (char) (((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
                if (expected != actual) return false;
            } else if ((b1 & 0xF8) == 0xF0) {
                // 4-byte character (surrogate pair)
                if (pos + 2 >= endPos) return false; // Edge case: incomplete sequence
                byte b2 = segment.get(Layouts.BYTE, pos++);
                byte b3 = segment.get(Layouts.BYTE, pos++);
                byte b4 = segment.get(Layouts.BYTE, pos++);

                // Validate continuation bytes
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80)
                    return false; // Invalid UTF-8

                int codePoint =
                        ((b1 & 0x07) << 18)
                                | ((b2 & 0x3F) << 12)
                                | ((b3 & 0x3F) << 6)
                                | (b4 & 0x3F);

                // Validate code point range
                if (codePoint < 0x10000 || codePoint > 0x10FFFF) return false; // Invalid code point

                // Check high surrogate
                if (expected != Character.highSurrogate(codePoint)) return false;

                // Check low surrogate
                i++;
                if (i >= other.length()) return false; // Missing low surrogate in string
                if (other.charAt(i) != Character.lowSurrogate(codePoint)) return false;
            } else {
                // Invalid UTF-8 start byte (0xF8-0xFF or continuation byte in wrong place)
                return false;
            }
        }

        // Edge case: consumed all string chars but have leftover bytes
        return pos == endPos;
    }

    /**
     * Calculates the UTF-8 byte length of a Java String without allocating.
     *
     * @param str The string to measure.
     * @return The number of bytes required to encode the string in UTF-8.
     */
    private int calculateUtf8ByteLength(String str) {
        int byteLength = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 0x80) {
                byteLength += 1;
            } else if (c < 0x800) {
                byteLength += 2;
            } else if (Character.isSurrogate(c)) {
                // Surrogate pair = 4 bytes in UTF-8
                byteLength += 4;
                i++; // Skip the low surrogate
            } else {
                byteLength += 3;
            }
        }
        return byteLength;
    }

    /**
     * A zero-allocation method to compare this view with another Utf8View. Performs byte-by-byte
     * comparison without allocating any heap objects.
     *
     * <p>Note the comparison rules for special cases: Returns false if the other Utf8View is null.
     * Returns true if both views are invalid (null segment). Returns false if one view is invalid
     * and the other is valid.
     *
     * @param other The Utf8View to compare against.
     * @return true if the content is identical, false otherwise.
     */
    public boolean equals(Utf8View other) {

        // TODO: need to think if this can be optimized for ASCII only data for HFT flows.
        // need to consider SIMD/Vector API usage as well for even faster comparisons.

        if (other == null) {
            return false;
        }

        // Both invalid
        if (segment == null && other.segment == null) {
            return true;
        }

        // One invalid
        if (segment == null || other.segment == null) {
            return false;
        }

        // Different lengths
        if (length != other.length) {
            return false;
        }

        // Byte-by-byte comparison
        for (int i = 0; i < length; i++) {
            byte b1 = segment.get(ValueLayout.JAVA_BYTE, offset + i);
            byte b2 = other.segment.get(ValueLayout.JAVA_BYTE, other.offset + i);
            if (b1 != b2) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares this view to another Utf8View lexicographically. Performs zero-allocation
     * byte-by-byte comparison.
     *
     * <p>Note the comparison rules for special cases: Throws IllegalArgumentException if the other
     * Utf8View is null. Considers two invalid views (null segments) as equal (returns 0). Returns
     * -1 if this view is invalid and the other Utf8View is valid. Returns 1 if this view is valid
     * and the other Utf8View is invalid.
     *
     * @param other The Utf8View to compare against.
     * @return negative if this &lt; other, 0 if equal, positive if this &gt; other.
     * @throws IllegalArgumentException if other is null.
     */
    public int compareTo(Utf8View other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot compare to null");
        }

        if (segment == null && other.segment == null) {
            return 0;
        }
        if (segment == null) {
            return -1;
        }
        if (other.segment == null) {
            return 1;
        }

        int minLength = Math.min(length, other.length);
        for (int position = 0; position < minLength; position++) {
            int thisByte = segment.get(ValueLayout.JAVA_BYTE, offset + position) & 0xFF;
            int otherByte =
                    other.segment.get(ValueLayout.JAVA_BYTE, other.offset + position) & 0xFF;
            if (thisByte != otherByte) {
                return thisByte - otherByte;
            }
        }

        return length - other.length;
    }

    /**
     * Computes a hash code for this view's content. Uses the same algorithm as String.hashCode()
     * but operates on UTF-8 bytes.
     *
     * <p><b>Note:</b> This method allocates no heap objects but may perform significant computation
     * for long strings.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        if (segment == null || length == 0) {
            return 0;
        }

        int hash = 0;
        for (int i = 0; i < length; i++) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, offset + i);
            hash = 31 * hash + (b & 0xFF);
        }
        return hash;
    }

    /**
     * Validates that this view contains well-formed UTF-8 data.
     *
     * <p>This method performs a complete validation of the UTF-8 byte sequence according to RFC
     * 3629, checking for:
     *
     * <ul>
     *   <li>Valid UTF-8 byte sequence structure (correct leading and continuation bytes)
     *   <li>Overlong encodings (e.g., using 2 bytes for ASCII)
     *   <li>Surrogate code points (U+D800-U+DFFF) which are invalid in UTF-8
     *   <li>Code points beyond U+10FFFF
     *   <li>Incomplete multi-byte sequences
     * </ul>
     *
     * <p><b>Performance:</b> This method is zero-allocation and O(n) in the byte length. For
     * high-throughput scenarios where data is trusted, validation may be skipped.
     *
     * @return {@link ValidationResult#VALID} if the data is well-formed UTF-8, or an error result
     *     indicating the type and position of the first error
     */
    public ValidationResult validateUtf8() {
        if (segment == null || length == 0) {
            return ValidationResult.VALID;
        }

        long pos = offset;
        long endPos = offset + length;

        while (pos < endPos) {
            byte b1 = segment.get(Layouts.BYTE, pos);
            int firstByte = b1 & 0xFF;

            if (firstByte < 0x80) {
                // 1-byte ASCII (0x00-0x7F) - always valid
                pos++;
            } else if (firstByte < 0xC0) {
                // Continuation byte in wrong place (0x80-0xBF)
                return ValidationResult.error(
                        ValidationError.UNEXPECTED_CONTINUATION, pos - offset);
            } else if (firstByte < 0xE0) {
                // 2-byte sequence (0xC0-0xDF)
                if (pos + 1 >= endPos) {
                    return ValidationResult.error(
                            ValidationError.INCOMPLETE_SEQUENCE, pos - offset);
                }
                byte b2 = segment.get(Layouts.BYTE, pos + 1);

                // Check continuation byte
                if ((b2 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 1);
                }

                // Check for overlong encoding (code points < 0x80 should use 1 byte)
                int codePoint = ((firstByte & 0x1F) << 6) | (b2 & 0x3F);
                if (codePoint < 0x80) {
                    return ValidationResult.error(ValidationError.OVERLONG_ENCODING, pos - offset);
                }

                pos += 2;
            } else if (firstByte < 0xF0) {
                // 3-byte sequence (0xE0-0xEF)
                if (pos + 2 >= endPos) {
                    return ValidationResult.error(
                            ValidationError.INCOMPLETE_SEQUENCE, pos - offset);
                }
                byte b2 = segment.get(Layouts.BYTE, pos + 1);
                byte b3 = segment.get(Layouts.BYTE, pos + 2);

                // Check continuation bytes
                if ((b2 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 1);
                }
                if ((b3 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 2);
                }

                int codePoint = ((firstByte & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);

                // Check for overlong encoding
                if (codePoint < 0x800) {
                    return ValidationResult.error(ValidationError.OVERLONG_ENCODING, pos - offset);
                }

                // Check for surrogate code points (U+D800-U+DFFF are invalid in UTF-8)
                if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
                    return ValidationResult.error(
                            ValidationError.SURROGATE_CODE_POINT, pos - offset);
                }

                pos += 3;
            } else if (firstByte < 0xF8) {
                // 4-byte sequence (0xF0-0xF7)
                if (pos + 3 >= endPos) {
                    return ValidationResult.error(
                            ValidationError.INCOMPLETE_SEQUENCE, pos - offset);
                }
                byte b2 = segment.get(Layouts.BYTE, pos + 1);
                byte b3 = segment.get(Layouts.BYTE, pos + 2);
                byte b4 = segment.get(Layouts.BYTE, pos + 3);

                // Check continuation bytes
                if ((b2 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 1);
                }
                if ((b3 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 2);
                }
                if ((b4 & 0xC0) != 0x80) {
                    return ValidationResult.error(
                            ValidationError.INVALID_CONTINUATION, pos - offset + 3);
                }

                int codePoint =
                        ((firstByte & 0x07) << 18)
                                | ((b2 & 0x3F) << 12)
                                | ((b3 & 0x3F) << 6)
                                | (b4 & 0x3F);

                // Check for overlong encoding
                if (codePoint < 0x10000) {
                    return ValidationResult.error(ValidationError.OVERLONG_ENCODING, pos - offset);
                }

                // Check for code points beyond U+10FFFF
                if (codePoint > 0x10FFFF) {
                    return ValidationResult.error(ValidationError.INVALID_CODE_POINT, pos - offset);
                }

                pos += 4;
            } else {
                // Invalid leading byte (0xF8-0xFF)
                return ValidationResult.error(ValidationError.INVALID_LEADING_BYTE, pos - offset);
            }
        }

        return ValidationResult.VALID;
    }

    /**
     * Returns true if this view contains well-formed UTF-8 data.
     *
     * <p>This is a convenience method equivalent to {@code validateUtf8().isValid()}.
     *
     * @return true if the data is valid UTF-8, false otherwise
     */
    public boolean isValidUtf8() {
        return validateUtf8().isValid();
    }

    /** Result of UTF-8 validation, containing error type and position if invalid. */
    public static final class ValidationResult {
        /** Singleton for valid UTF-8 data (zero allocation for the common case). */
        public static final ValidationResult VALID = new ValidationResult(null, -1);

        private final ValidationError error;
        private final long errorOffset;

        private ValidationResult(ValidationError error, long errorOffset) {
            this.error = error;
            this.errorOffset = errorOffset;
        }

        /** Returns the error type, or null if valid. */
        public ValidationError error() {
            return error;
        }

        static ValidationResult error(ValidationError error, long offset) {
            return new ValidationResult(error, offset);
        }

        /** Returns the byte offset of the error within the view, or -1 if valid. */
        public long errorOffset() {
            return errorOffset;
        }

        /** Returns true if the UTF-8 data is valid. */
        public boolean isValid() {
            return error == null;
        }

        @Override
        public String toString() {
            if (isValid()) {
                return "ValidationResult[VALID]";
            }
            return "ValidationResult[error=" + error + ", offset=" + errorOffset + "]";
        }
    }

    /** Types of UTF-8 validation errors. */
    public enum ValidationError {
        /** Continuation byte (0x80-0xBF) found where a leading byte was expected. */
        UNEXPECTED_CONTINUATION,
        /** Invalid continuation byte in a multi-byte sequence. */
        INVALID_CONTINUATION,
        /** Multi-byte sequence is incomplete (truncated at end of data). */
        INCOMPLETE_SEQUENCE,
        /** Overlong encoding (code point encoded with more bytes than necessary). */
        OVERLONG_ENCODING,
        /** Invalid leading byte (0xF8-0xFF). */
        INVALID_LEADING_BYTE,
        /** Code point beyond U+10FFFF. */
        INVALID_CODE_POINT,
        /** Surrogate code point (U+D800-U+DFFF) which is invalid in UTF-8. */
        SURROGATE_CODE_POINT
    }
}
