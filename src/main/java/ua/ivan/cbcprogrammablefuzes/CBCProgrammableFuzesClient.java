package ua.ivan.cbcprogrammablefuzes;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

final class CBCProgrammableFuzesClient {
    private CBCProgrammableFuzesClient() {
    }

    static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (ignoredContainer, parent) -> new BaseConfigScreen(parent, CBCProgrammableFuzes.MOD_ID));
    }
}
