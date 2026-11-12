package net.xpack.companion.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 : PauseScreen ne publie pas encore showsPauseMenu(). */
@Mixin(PauseScreen.class)
public interface PauseScreenAccessor {
    @Accessor("showPauseMenu")
    boolean xpack$showPauseMenu();
}
