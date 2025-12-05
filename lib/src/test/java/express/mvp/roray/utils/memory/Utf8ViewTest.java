package express.mvp.roray.utils.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for Utf8View covering edge cases, zero-GC operations, comparison methods, and
 * UTF-8 validation.
 */
class Utf8ViewTest {

    private Arena arena;
    private Utf8View view1;
    private Utf8View view2;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        view1 = new Utf8View();
        view2 = new Utf8View();
    }

    @AfterEach
    void tearDown() {
        arena.close();
    }

    // =================================================================
    // Basic Functionality Tests
    // =================================================================

    @Test
    void testWrapAndToString() {
        String testString = "Hello, World!";
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertEquals(testString, view1.toString());
        assertEquals(utf8Bytes.length, view1.byteSize());
        assertEquals(0, view1.offset());
        assertSame(segment, view1.segment());
        assertTrue(view1.isValid());
    }

    @Test
    void testEmptyString() {
        MemorySegment segment = arena.allocate(1);
        view1.wrap(segment, 0, 0);

        assertEquals("", view1.toString());
        assertEquals(0, view1.byteSize());
        assertTrue(view1.isValid());
    }

    @Test
    void testUnwrappedView() {
        assertFalse(view1.isValid());
        assertEquals("", view1.toString());
        assertNull(view1.segment());
    }

    // =================================================================
    // equalsString() Zero-GC Tests
    // =================================================================

    @Test
    void testEqualsString_Simple() {
        String testString = "test";
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString("test"));
        assertFalse(view1.equalsString("Test"));
        assertFalse(view1.equalsString("test2"));
        assertFalse(view1.equalsString("tes"));
        assertFalse(view1.equalsString(null));
    }

    @Test
    void testEqualsString_EmptyString() {
        MemorySegment segment = arena.allocate(1);
        view1.wrap(segment, 0, 0);

        assertTrue(view1.equalsString(""));
        assertFalse(view1.equalsString("a"));
    }

    @Test
    void testEqualsString_UnwrappedView() {
        assertTrue(view1.equalsString(""));
        assertFalse(view1.equalsString("test"));
    }

    @Test
    void testEqualsString_MultiByteCharacters() {
        // Test with various UTF-8 byte lengths
        String testString = "café"; // 'é' is 2 bytes in UTF-8
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString("café"));
        assertFalse(view1.equalsString("cafe"));
    }

    @Test
    void testEqualsString_ChineseCharacters() {
        String testString = "你好世界"; // Chinese characters (3 bytes each in UTF-8)
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString("你好世界"));
        assertFalse(view1.equalsString("你好"));
    }

    @Test
    void testEqualsString_Emoji() {
        String testString = "Hello 👋🌍"; // Emojis are 4 bytes in UTF-8
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString("Hello 👋🌍"));
        assertFalse(view1.equalsString("Hello"));
    }

    @Test
    void testEqualsString_MixedCharacters() {
        String testString = "ASCII-café-你好-🎉"; // Mix of 1,2,3,4 byte chars
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString("ASCII-café-你好-🎉"));
        assertFalse(view1.equalsString("ASCII-cafe-你好-🎉"));
    }

    // =================================================================
    // equals(Utf8View) Tests
    // =================================================================

    @Test
    void testEqualsView_Identical() {
        String testString = "test";
        byte[] utf8Bytes = testString.getBytes(StandardCharsets.UTF_8);

        MemorySegment segment1 = arena.allocate(utf8Bytes.length);
        MemorySegment segment2 = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment1, 0, utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment2, 0, utf8Bytes.length);

        view1.wrap(segment1, 0, utf8Bytes.length);
        view2.wrap(segment2, 0, utf8Bytes.length);

        assertTrue(view1.equals(view2));
        assertTrue(view2.equals(view1));
    }

    @Test
    void testEqualsView_Different() {
        String string1 = "test1";
        String string2 = "test2";

        MemorySegment segment1 = createSegmentFromString(string1);
        MemorySegment segment2 = createSegmentFromString(string2);

        view1.wrap(segment1, 0, string1.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, string2.getBytes(StandardCharsets.UTF_8).length);

        assertFalse(view1.equals(view2));
    }

    @Test
    void testEqualsView_DifferentLengths() {
        String string1 = "test";
        String string2 = "testing";

        MemorySegment segment1 = createSegmentFromString(string1);
        MemorySegment segment2 = createSegmentFromString(string2);

        view1.wrap(segment1, 0, string1.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, string2.getBytes(StandardCharsets.UTF_8).length);

        assertFalse(view1.equals(view2));
    }

    @Test
    void testEqualsView_BothUnwrapped() {
        assertTrue(view1.equals(view2));
    }

    @Test
    void testEqualsView_OneUnwrapped() {
        String testString = "test";
        MemorySegment segment = createSegmentFromString(testString);
        view1.wrap(segment, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        assertFalse(view1.equals(view2));
        assertFalse(view2.equals(view1));
    }

    @Test
    void testEqualsView_Null() {
        assertFalse(view1.equals(null));

        String testString = "test";
        MemorySegment segment = createSegmentFromString(testString);
        view1.wrap(segment, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        assertFalse(view1.equals(null));
    }

    // =================================================================
    // compareTo() Tests
    // =================================================================

    @Test
    void testCompareTo_Equal() {
        String testString = "test";
        MemorySegment segment1 = createSegmentFromString(testString);
        MemorySegment segment2 = createSegmentFromString(testString);

        view1.wrap(segment1, 0, testString.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        assertEquals(0, view1.compareTo(view2));
        assertEquals(0, view2.compareTo(view1));
    }

    @Test
    void testCompareTo_LessThan() {
        String string1 = "apple";
        String string2 = "banana";

        MemorySegment segment1 = createSegmentFromString(string1);
        MemorySegment segment2 = createSegmentFromString(string2);

        view1.wrap(segment1, 0, string1.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, string2.getBytes(StandardCharsets.UTF_8).length);

        assertTrue(view1.compareTo(view2) < 0);
        assertTrue(view2.compareTo(view1) > 0);
    }

    @Test
    void testCompareTo_Prefix() {
        String string1 = "test";
        String string2 = "testing";

        MemorySegment segment1 = createSegmentFromString(string1);
        MemorySegment segment2 = createSegmentFromString(string2);

        view1.wrap(segment1, 0, string1.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, string2.getBytes(StandardCharsets.UTF_8).length);

        assertTrue(view1.compareTo(view2) < 0);
        assertTrue(view2.compareTo(view1) > 0);
    }

    @Test
    void testCompareTo_UnwrappedViews() {
        assertEquals(0, view1.compareTo(view2));
    }

    @Test
    void testCompareTo_OneUnwrapped() {
        String testString = "test";
        MemorySegment segment = createSegmentFromString(testString);
        view1.wrap(segment, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        assertTrue(view1.compareTo(view2) > 0);
        assertTrue(view2.compareTo(view1) < 0);
    }

    @Test
    void testCompareTo_Null() {
        assertThrows(IllegalArgumentException.class, () -> view1.compareTo(null));
    }

    // =================================================================
    // hashCode() Tests
    // =================================================================

    @Test
    void testHashCode_Consistent() {
        String testString = "test";
        MemorySegment segment = createSegmentFromString(testString);
        view1.wrap(segment, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        int hash1 = view1.hashCode();
        int hash2 = view1.hashCode();

        assertEquals(hash1, hash2);
    }

    @Test
    void testHashCode_EqualViewsHaveSameHash() {
        String testString = "test";
        MemorySegment segment1 = createSegmentFromString(testString);
        MemorySegment segment2 = createSegmentFromString(testString);

        view1.wrap(segment1, 0, testString.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, testString.getBytes(StandardCharsets.UTF_8).length);

        assertEquals(view1.hashCode(), view2.hashCode());
    }

    @Test
    void testHashCode_DifferentContentsDifferentHash() {
        String string1 = "test1";
        String string2 = "test2";

        MemorySegment segment1 = createSegmentFromString(string1);
        MemorySegment segment2 = createSegmentFromString(string2);

        view1.wrap(segment1, 0, string1.getBytes(StandardCharsets.UTF_8).length);
        view2.wrap(segment2, 0, string2.getBytes(StandardCharsets.UTF_8).length);

        assertNotEquals(view1.hashCode(), view2.hashCode());
    }

    @Test
    void testHashCode_EmptyView() {
        assertEquals(0, view1.hashCode());

        MemorySegment segment = arena.allocate(1);
        view1.wrap(segment, 0, 0);
        assertEquals(0, view1.hashCode());
    }

    // =================================================================
    // Edge Cases and Special Characters
    // =================================================================

    @Test
    void testSpecialCharacters() {
        String[] specialStrings = {
            "\n\r\t",
            "\"'`",
            "\\",
            "\u0000", // NULL character
            "\u001F", // Unit separator
            "mixed\nline\tbreaks\r\ntext"
        };

        for (String str : specialStrings) {
            byte[] utf8Bytes = str.getBytes(StandardCharsets.UTF_8);
            MemorySegment segment = arena.allocate(utf8Bytes.length);
            MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

            view1.wrap(segment, 0, utf8Bytes.length);

            assertTrue(view1.equalsString(str), "Failed for: " + str);
            assertEquals(str, view1.toString());
        }
    }

    @Test
    void testLongString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Long string with repetition ");
        }
        String longString = sb.toString();

        byte[] utf8Bytes = longString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString(longString));
        assertEquals(longString, view1.toString());
    }

    @Test
    void testAllSingleByteCharacters() {
        StringBuilder sb = new StringBuilder();
        for (char c = 32; c < 127; c++) { // Printable ASCII
            sb.append(c);
        }
        String asciiString = sb.toString();

        byte[] utf8Bytes = asciiString.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

        view1.wrap(segment, 0, utf8Bytes.length);

        assertTrue(view1.equalsString(asciiString));
    }

    @Test
    void testVariousEmojis() {
        String[] emojiStrings = {
            "😀😃😄😁",
            "👨‍👩‍👧‍👦", // Family emoji with ZWJ
            "🏳️‍🌈", // Flag with variation selector
            "🇺🇸🇬🇧🇯🇵", // Country flags
            "1️⃣2️⃣3️⃣" // Keycap emojis
        };

        for (String str : emojiStrings) {
            byte[] utf8Bytes = str.getBytes(StandardCharsets.UTF_8);
            MemorySegment segment = arena.allocate(utf8Bytes.length);
            MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);

            view1.wrap(segment, 0, utf8Bytes.length);

            assertTrue(view1.equalsString(str), "Failed for: " + str);
            assertEquals(str, view1.toString());
        }
    }

    // =================================================================
    // Helper Methods
    // =================================================================

    private MemorySegment createSegmentFromString(String str) {
        byte[] utf8Bytes = str.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = arena.allocate(utf8Bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(utf8Bytes), 0, segment, 0, utf8Bytes.length);
        return segment;
    }

    private MemorySegment createSegmentFromBytes(byte[] bytes) {
        MemorySegment segment = arena.allocate(bytes.length);
        MemorySegment.copy(MemorySegment.ofArray(bytes), 0, segment, 0, bytes.length);
        return segment;
    }

    // =================================================================
    // UTF-8 Validation Tests
    // =================================================================

    @Test
    void testValidateUtf8_EmptyIsValid() {
        MemorySegment segment = arena.allocate(1);
        view1.wrap(segment, 0, 0);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertTrue(result.isValid());
        assertTrue(view1.isValidUtf8());
    }

    @Test
    void testValidateUtf8_UnwrappedIsValid() {
        // Unwrapped view is considered valid (empty)
        assertTrue(view1.validateUtf8().isValid());
    }

    @Test
    void testValidateUtf8_AsciiIsValid() {
        MemorySegment segment = createSegmentFromString("Hello, World!");
        view1.wrap(segment, 0, (int) segment.byteSize());

        assertTrue(view1.validateUtf8().isValid());
        assertTrue(view1.isValidUtf8());
    }

    @Test
    void testValidateUtf8_TwoByteIsValid() {
        // Latin characters requiring 2 bytes: é (U+00E9), ñ (U+00F1)
        String str = "café español";
        MemorySegment segment = createSegmentFromString(str);
        view1.wrap(segment, 0, (int) segment.byteSize());

        assertTrue(view1.validateUtf8().isValid());
    }

    @Test
    void testValidateUtf8_ThreeByteIsValid() {
        // Japanese characters requiring 3 bytes
        String str = "日本語テスト";
        MemorySegment segment = createSegmentFromString(str);
        view1.wrap(segment, 0, (int) segment.byteSize());

        assertTrue(view1.validateUtf8().isValid());
    }

    @Test
    void testValidateUtf8_FourByteIsValid() {
        // Emoji requiring 4 bytes
        String str = "Hello 😀 World 🌍";
        MemorySegment segment = createSegmentFromString(str);
        view1.wrap(segment, 0, (int) segment.byteSize());

        assertTrue(view1.validateUtf8().isValid());
    }

    @Test
    void testValidateUtf8_MixedCharactersIsValid() {
        // Mix of 1, 2, 3, and 4 byte characters
        String str = "Hello café 日本語 😀";
        MemorySegment segment = createSegmentFromString(str);
        view1.wrap(segment, 0, (int) segment.byteSize());

        assertTrue(view1.validateUtf8().isValid());
    }

    @Test
    void testValidateUtf8_UnexpectedContinuation() {
        // Start with a continuation byte (0x80-0xBF)
        byte[] bytes = {(byte) 0x80, 0x41, 0x42};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.UNEXPECTED_CONTINUATION, result.error());
        assertEquals(0, result.errorOffset());
    }

    @Test
    void testValidateUtf8_InvalidContinuation() {
        // 2-byte sequence with invalid continuation (should be 0x80-0xBF)
        byte[] bytes = {(byte) 0xC2, 0x00}; // Second byte should be 0x80-0xBF
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INVALID_CONTINUATION, result.error());
        assertEquals(1, result.errorOffset());
    }

    @Test
    void testValidateUtf8_IncompleteSequence_TwoByte() {
        // Start of 2-byte sequence but no second byte
        byte[] bytes = {(byte) 0xC2};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INCOMPLETE_SEQUENCE, result.error());
    }

    @Test
    void testValidateUtf8_IncompleteSequence_ThreeByte() {
        // Start of 3-byte sequence but only 1 continuation byte
        byte[] bytes = {(byte) 0xE0, (byte) 0xA0};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INCOMPLETE_SEQUENCE, result.error());
    }

    @Test
    void testValidateUtf8_IncompleteSequence_FourByte() {
        // Start of 4-byte sequence but only 2 continuation bytes
        byte[] bytes = {(byte) 0xF0, (byte) 0x90, (byte) 0x80};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INCOMPLETE_SEQUENCE, result.error());
    }

    @Test
    void testValidateUtf8_OverlongEncoding_TwoByte() {
        // Overlong encoding of ASCII 'A' (0x41) using 2 bytes
        // Should be 0x41, but encoded as 0xC1 0x81
        byte[] bytes = {(byte) 0xC1, (byte) 0x81};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.OVERLONG_ENCODING, result.error());
    }

    @Test
    void testValidateUtf8_InvalidLeadingByte() {
        // Invalid leading bytes (0xF8-0xFF)
        byte[] bytes = {(byte) 0xF8};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INVALID_LEADING_BYTE, result.error());
    }

    @Test
    void testValidateUtf8_SurrogateCodePoint() {
        // U+D800 is a surrogate code point, invalid in UTF-8
        // Encoded as: 0xED 0xA0 0x80
        byte[] bytes = {(byte) 0xED, (byte) 0xA0, (byte) 0x80};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.SURROGATE_CODE_POINT, result.error());
    }

    @Test
    void testValidateUtf8_InvalidCodePoint() {
        // Code point > U+10FFFF
        // Try to encode U+110000 (invalid)
        // This would be encoded as 0xF4 0x90 0x80 0x80 which is > 0x10FFFF
        byte[] bytes = {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.INVALID_CODE_POINT, result.error());
    }

    @Test
    void testValidateUtf8_ErrorInMiddle() {
        // Valid ASCII followed by invalid sequence
        byte[] bytes = {'H', 'i', (byte) 0x80, 'X'}; // 0x80 is unexpected continuation
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult result = view1.validateUtf8();
        assertFalse(result.isValid());
        assertEquals(Utf8View.ValidationError.UNEXPECTED_CONTINUATION, result.error());
        assertEquals(2, result.errorOffset()); // Error at position 2
    }

    @Test
    void testValidateUtf8_ValidationResultToString() {
        Utf8View.ValidationResult valid = Utf8View.ValidationResult.VALID;
        assertEquals("ValidationResult[VALID]", valid.toString());

        byte[] bytes = {(byte) 0x80};
        MemorySegment segment = createSegmentFromBytes(bytes);
        view1.wrap(segment, 0, bytes.length);

        Utf8View.ValidationResult invalid = view1.validateUtf8();
        assertTrue(invalid.toString().contains("UNEXPECTED_CONTINUATION"));
        assertTrue(invalid.toString().contains("offset=0"));
    }
}
