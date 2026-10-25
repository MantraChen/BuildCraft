package buildcraft.lib.gui;

import java.util.Objects;

import net.minecraft.client.gui.DrawContext;

/** Makes the current native frame available to the existing drawable callback API.
 * Scopes must be closed before returning from render; a context is never kept across frames. */
public final class GuiDrawContext implements AutoCloseable {
    private static final ThreadLocal<GuiDrawContext> CURRENT = new ThreadLocal<>();
    private final GuiDrawContext previous;
    private final DrawContext context;
    private boolean closed;

    private GuiDrawContext(DrawContext context) {
        this.context = Objects.requireNonNull(context, "context");
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static GuiDrawContext use(DrawContext context) {
        return new GuiDrawContext(context);
    }

    public static DrawContext current() {
        GuiDrawContext frame = CURRENT.get();
        if (frame == null) throw new IllegalStateException("GUI drawing outside a DrawContext frame");
        return frame.context;
    }

    @Override
    public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("GUI scopes closed out of order");
        closed = true;
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
