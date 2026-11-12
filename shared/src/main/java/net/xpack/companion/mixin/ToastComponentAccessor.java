package net.xpack.companion.mixin;

import java.util.Deque;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** File d'attente des toasts du jeu : paliers et alertes XPack passent après (XPackToasts). */
@Mixin(ToastComponent.class)
public interface ToastComponentAccessor {
    @Accessor("queued")
    Deque<Toast> xpack$queued();
}
