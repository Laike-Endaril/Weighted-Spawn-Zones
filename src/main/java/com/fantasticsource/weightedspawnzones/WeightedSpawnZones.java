package com.fantasticsource.weightedspawnzones;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod(modid = WeightedSpawnZones.MODID, name = WeightedSpawnZones.NAME, version = WeightedSpawnZones.VERSION, dependencies = "required-after:fantasticlib@[1.12.2.044zg,)")
public class WeightedSpawnZones
{
    public static final String MODID = "weightedspawnzones";
    public static final String NAME = "Weighted Spawn Zones";
    public static final String VERSION = "1.12.2.000";

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event)
    {
        MinecraftForge.EVENT_BUS.register(WeightedSpawnZones.class);
        MinecraftForge.EVENT_BUS.register(CSpawnDefinition.class);
    }

    @SubscribeEvent
    public static void saveConfig(ConfigChangedEvent.OnConfigChangedEvent event)
    {
        if (event.getModID().equals(MODID)) ConfigManager.sync(MODID, Config.Type.INSTANCE);
    }
}
