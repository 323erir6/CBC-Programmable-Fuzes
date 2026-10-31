package ua.ivan.cbcprogrammablefuzes;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.ForgeComputerCraftAPI;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rbasamoyai.createbigcannons.ModGroup;
import rbasamoyai.createbigcannons.munitions.fuzes.ProximityFuzeItem;
import ua.ivan.cbcprogrammablefuzes.peripheral.FuzeCapabilities;
import ua.ivan.cbcprogrammablefuzes.peripheral.FuzePeripheralMethods;

@Mod(CBCProgrammableFuzes.MOD_ID)
public final class CBCProgrammableFuzes {
    public static final String MOD_ID = "cbc_programmable_fuzes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<ProgrammableFuzeItem> PROGRAMMABLE_FUZE = ITEMS.registerItem(
        "programmable_fuze",
        ProgrammableFuzeItem::new,
        new Item.Properties().stacksTo(1)
    );
    public static final DeferredItem<TrajectoryCorrectionModuleItem> TRAJECTORY_CORRECTION_MODULE = ITEMS.registerItem(
        "trajectory_correction_module",
        TrajectoryCorrectionModuleItem::new,
        new Item.Properties().stacksTo(1)
    );

    public CBCProgrammableFuzes(IEventBus modBus, ModContainer container, Dist dist) {
        container.registerConfig(
            ModConfig.Type.SERVER,
            FuzeConfig.SPEC,
            "cbc-programmable-fuzes-server.toml"
        );
        ITEMS.register(modBus);
        modBus.addListener(FuzeCapabilities::register);
        modBus.addListener(CBCProgrammableFuzes::addCreativeTabContents);

        ComputerCraftAPI.registerGenericSource(new FuzePeripheralMethods());
        ForgeComputerCraftAPI.registerGenericCapability(FuzeCapabilities.PROGRAMMABLE_FUZE);
        NeoForge.EVENT_BUS.addListener(CorrectionModuleInteractionHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ProgrammableFuzeItem::onPostPhysicsTick);
        NeoForge.EVENT_BUS.addListener(CBCProgrammableFuzes::onServerStopped);

        if (dist == Dist.CLIENT) {
            CBCProgrammableFuzesClient.registerConfigScreen(container);
        }
    }

    private static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        CorrectionTelemetryRegistry.clear();
        InFlightFuzeRegistry.clear();
        ProgrammableFuzeItem.clearPhysicalRuntime();
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModGroup.MAIN_TAB_KEY)) {
            event.insertAfter(
                ProximityFuzeItem.getCreativeTabItem(1),
                new ItemStack(PROGRAMMABLE_FUZE.get()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
            event.insertAfter(
                new ItemStack(PROGRAMMABLE_FUZE.get()),
                new ItemStack(TRAJECTORY_CORRECTION_MODULE.get()),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
            );
        }
    }
}
