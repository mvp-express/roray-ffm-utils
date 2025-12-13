package express.mvp.roray.ffm.utils.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link FunctionDescriptorBuilder}. */
class FunctionDescriptorBuilderTest {

    @Test
    @DisplayName("returnsVoid() should create void-returning descriptor")
    void returnsVoid_shouldCreateVoidDescriptor() {
        FunctionDescriptor desc = FunctionDescriptorBuilder.returnsVoid().build();

        assertTrue(desc.returnLayout().isEmpty(), "Should have no return layout");
        assertEquals(0, desc.argumentLayouts().size(), "Should have no arguments");
    }

    @Test
    @DisplayName("returnsVoid() with args should create correct descriptor")
    void returnsVoid_withArgs_shouldCreateCorrectDescriptor() {
        FunctionDescriptor desc =
                FunctionDescriptorBuilder.returnsVoid()
                        .args(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
                        .build();

        assertTrue(desc.returnLayout().isEmpty());
        assertEquals(2, desc.argumentLayouts().size());
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(0));
        assertEquals(ValueLayout.ADDRESS, desc.argumentLayouts().get(1));
    }

    @Test
    @DisplayName("returnsInt() should create int-returning descriptor")
    void returnsInt_shouldCreateIntDescriptor() {
        FunctionDescriptor desc = FunctionDescriptorBuilder.returnsInt().build();

        assertTrue(desc.returnLayout().isPresent());
        assertEquals(ValueLayout.JAVA_INT, desc.returnLayout().get());
    }

    @Test
    @DisplayName("returnsLong() should create long-returning descriptor")
    void returnsLong_shouldCreateLongDescriptor() {
        FunctionDescriptor desc = FunctionDescriptorBuilder.returnsLong().build();

        assertTrue(desc.returnLayout().isPresent());
        assertEquals(ValueLayout.JAVA_LONG, desc.returnLayout().get());
    }

    @Test
    @DisplayName("returnsPointer() should create pointer-returning descriptor")
    void returnsPointer_shouldCreatePointerDescriptor() {
        FunctionDescriptor desc = FunctionDescriptorBuilder.returnsPointer().build();

        assertTrue(desc.returnLayout().isPresent());
        assertEquals(ValueLayout.ADDRESS, desc.returnLayout().get());
    }

    @Test
    @DisplayName("returns() should accept custom layout")
    void returns_shouldAcceptCustomLayout() {
        FunctionDescriptor desc =
                FunctionDescriptorBuilder.returns(ValueLayout.JAVA_DOUBLE).build();

        assertTrue(desc.returnLayout().isPresent());
        assertEquals(ValueLayout.JAVA_DOUBLE, desc.returnLayout().get());
    }

    @Test
    @DisplayName("args() should accept multiple layouts")
    void args_shouldAcceptMultipleLayouts() {
        FunctionDescriptor desc =
                FunctionDescriptorBuilder.returnsInt()
                        .args(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)
                        .build();

        assertEquals(3, desc.argumentLayouts().size());
    }

    @Test
    @DisplayName("arg() should add single layout")
    void arg_shouldAddSingleLayout() {
        FunctionDescriptor desc =
                FunctionDescriptorBuilder.returnsInt()
                        .arg(ValueLayout.JAVA_INT)
                        .arg(ValueLayout.JAVA_LONG)
                        .build();

        assertEquals(2, desc.argumentLayouts().size());
        assertEquals(ValueLayout.JAVA_INT, desc.argumentLayouts().get(0));
        assertEquals(ValueLayout.JAVA_LONG, desc.argumentLayouts().get(1));
    }

    @Test
    @DisplayName("args() and arg() can be chained")
    void args_andArg_canBeChained() {
        FunctionDescriptor desc =
                FunctionDescriptorBuilder.returnsVoid()
                        .args(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
                        .arg(ValueLayout.ADDRESS)
                        .args(ValueLayout.JAVA_LONG)
                        .build();

        assertEquals(4, desc.argumentLayouts().size());
    }

    @Test
    @DisplayName("empty args() should work")
    void emptyArgs_shouldWork() {
        FunctionDescriptor desc = FunctionDescriptorBuilder.returnsInt().args().build();

        assertEquals(0, desc.argumentLayouts().size());
    }

    @Test
    @DisplayName("descriptor should match direct creation")
    void descriptor_shouldMatchDirectCreation() {
        FunctionDescriptor builderDesc =
                FunctionDescriptorBuilder.returnsInt()
                        .args(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG)
                        .build();

        FunctionDescriptor directDesc =
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG);

        assertEquals(directDesc, builderDesc);
    }

    @Test
    @DisplayName("void descriptor should match direct creation")
    void voidDescriptor_shouldMatchDirectCreation() {
        FunctionDescriptor builderDesc =
                FunctionDescriptorBuilder.returnsVoid()
                        .args(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
                        .build();

        FunctionDescriptor directDesc =
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT);

        assertEquals(directDesc, builderDesc);
    }

    @Test
    @DisplayName("builder should be reusable pattern (new builder each time)")
    void builder_shouldBeReusablePattern() {
        // Each static method creates a new builder
        FunctionDescriptor desc1 =
                FunctionDescriptorBuilder.returnsInt().args(ValueLayout.JAVA_INT).build();

        FunctionDescriptor desc2 =
                FunctionDescriptorBuilder.returnsInt().args(ValueLayout.JAVA_LONG).build();

        assertNotEquals(desc1, desc2);
    }
}
