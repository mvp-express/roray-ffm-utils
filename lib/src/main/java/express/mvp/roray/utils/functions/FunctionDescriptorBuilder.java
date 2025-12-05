package express.mvp.roray.utils.functions;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link FunctionDescriptor} - all costs are paid at setup time. Zero runtime
 * overhead as this produces standard {@code FunctionDescriptor} instances.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * FunctionDescriptor desc = FunctionDescriptorBuilder.returnsInt()
 *     .args(ValueLayout.ADDRESS, ValueLayout.JAVA_INT)
 *     .build();
 * }</pre>
 */
public final class FunctionDescriptorBuilder {

    private MemoryLayout returnLayout;
    private final List<MemoryLayout> argLayouts = new ArrayList<>();

    private FunctionDescriptorBuilder() {}

    /** Start building a descriptor with the specified return layout. */
    public static FunctionDescriptorBuilder returns(MemoryLayout layout) {
        var builder = new FunctionDescriptorBuilder();
        builder.returnLayout = layout;
        return builder;
    }

    /** Start building a descriptor that returns void. */
    public static FunctionDescriptorBuilder returnsVoid() {
        return new FunctionDescriptorBuilder();
    }

    /** Start building a descriptor that returns an int (C int, 32-bit). */
    public static FunctionDescriptorBuilder returnsInt() {
        return returns(ValueLayout.JAVA_INT);
    }

    /** Start building a descriptor that returns a long (C long on Linux x86_64, 64-bit). */
    public static FunctionDescriptorBuilder returnsLong() {
        return returns(ValueLayout.JAVA_LONG);
    }

    /** Start building a descriptor that returns a pointer. */
    public static FunctionDescriptorBuilder returnsPointer() {
        return returns(ValueLayout.ADDRESS);
    }

    /** Add argument layouts to this descriptor. */
    public FunctionDescriptorBuilder args(MemoryLayout... layouts) {
        for (MemoryLayout layout : layouts) {
            argLayouts.add(layout);
        }
        return this;
    }

    /** Add a single argument layout. */
    public FunctionDescriptorBuilder arg(MemoryLayout layout) {
        argLayouts.add(layout);
        return this;
    }

    /** Build the {@link FunctionDescriptor}. */
    public FunctionDescriptor build() {
        MemoryLayout[] args = argLayouts.toArray(MemoryLayout[]::new);
        if (returnLayout == null) {
            return FunctionDescriptor.ofVoid(args);
        }
        return FunctionDescriptor.of(returnLayout, args);
    }
}
