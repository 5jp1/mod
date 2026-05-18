package com.modrinthdownloader.mixin;

import com.modrinthdownloader.client.gui.ModrinthBrowserScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the "Download Mods" button into the Minecraft main menu.
 * Uses Mojang mappings (required for 26.1+, no Yarn available).
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(at = @At("RETURN"), method = "init")
    private void init(CallbackInfo info) {
        TitleScreen self = (TitleScreen) (Object) this;

        // Access width/height via the Screen superclass fields
        int screenWidth = self.width;
        int screenHeight = self.height;

        Button downloadButton = Button.builder(
                Component.literal("\u2B07 Download Mods"),
                button -> {
                    // Navigate to our Modrinth browser screen
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new ModrinthBrowserScreen(self));
                }
        )
        .bounds(screenWidth / 2 - 100, screenHeight / 4 + 132, 200, 20)
        .build();

        // addRenderableWidget is the Mojang-mapped name for addDrawableChild
        self.addRenderableWidget(downloadButton);
    }
}
