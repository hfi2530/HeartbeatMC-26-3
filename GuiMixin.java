package net.heartbeat.mixin.client.modmenu;

import com.terraformersmc.modmenu.api.UpdateInfo;
import com.terraformersmc.modmenu.gui.ModsScreen;
import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import com.terraformersmc.modmenu.util.mod.Mod;
import net.heartbeat.HeartbeatClient;
import net.heartbeat.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Objects;

@Mixin(ModsScreen.class)
public class GuiMixin extends Screen {
    protected GuiMixin(Component title) { super(title); }

    @Shadow(remap = false) private ModListEntry selected;
    @Shadow(remap = false) @Final private static int RIGHT_PANE_Y;

    @Unique private AbstractWidget updateButton;
    @Unique private static final Component UPDATE_TOOLTIP = Component.translatable("gui.heartbeat.tooltip.update");
    @Unique private static final Component NO_UPDATE_AVAILABLE_TOOLTIP = Component.translatable("gui.heartbeat.tooltip.no_update_available");


    @Inject(method="init", at=@At("HEAD"))
    void onInit(CallbackInfo ci){
        this.updateButton = Button.builder(Component.literal("\u2191"), button -> {
                    // Update this mod
                    // I mean it shouldn't be rocket science, right????
                    final Mod mod = Objects.requireNonNull(selected).getMod();
                    final UpdateInfo info = Objects.requireNonNull(mod.getUpdateInfo());
                    final Path path = JarPathGetter.get(mod.getId()).orElseThrow();
                    final String hash = HashUtil.sha1(path);
                    final String fileName = path.getFileName().toString();

                    var api = new ModrinthApi();

                    api.getUpdateLink(hash).thenApply(hUpdateInfo -> {
                        // Already up to date, shouldn't happen
                        if(hUpdateInfo.isEmpty())
                            return 0;

                        var gHUpdateInfo = hUpdateInfo.get();

                        api.download(gHUpdateInfo.downloadURI(), gHUpdateInfo.fileName(), new ModInfo(fileName, mod.getName()));

                        return 1;
                    });

                    // Deactivate Button
                    button.active = false;
                    HeartbeatClient.updatedMods.add(mod.getId());
                })
                .bounds(width - 24 * 2, RIGHT_PANE_Y, 20, 20)
                .build();

        this.addRenderableWidget(this.updateButton);
    }

    // Refresh the update button when selected entry refreshes
    @Inject(method="updateSelectedEntry", at=@At("HEAD"), remap = false)
    void onRefreshEntry(ModListEntry entry, CallbackInfo ci){
        if(entry == null){
            this.updateButton.visible = false;
            return;
        }

        Mod mod = entry.getMod();
        UpdateInfo info = null;

        if(mod.getUpdateInfo() != null)
            info = mod.getUpdateInfo();

        this.updateButton.visible = mod.allowsUpdateChecks();
        this.updateButton.active = info != null && info.isUpdateAvailable() && !HeartbeatClient.updatedMods.contains(entry.mod.getId());
    }

    // Draw Tooltip when we hover update button
    @Inject(method="extractRenderState", at=@At("TAIL"))
    void onRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci){
        if(!updateButton.visible)
            return;

        Component ttr = updateButton.active ? UPDATE_TOOLTIP : NO_UPDATE_AVAILABLE_TOOLTIP;

        // Outside, so don't render
        if(mouseX < width - 24 * 2 ||
                mouseX >= (width - 24 * 2) + 20 ||
                mouseY < RIGHT_PANE_Y ||
                mouseY >= RIGHT_PANE_Y + 20)
            return;

        graphics.setTooltipForNextFrame(
                this.font,
                ttr,
                mouseX,
                mouseY
        );
    }
}
