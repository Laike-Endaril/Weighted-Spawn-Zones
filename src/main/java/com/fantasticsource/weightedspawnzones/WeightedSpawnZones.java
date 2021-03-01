package com.fantasticsource.weightedspawnzones;

import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;

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

    @Mod.EventHandler
    public static void serverStopped(FMLServerStoppedEvent event)
    {
        CSpawnDefinition.UNFOUND_ENTITIES.clear();
        CSpawnDefinition.SPAWN_DEFINITIONS.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void worldLoad(WorldEvent.Load event) throws NBTException
    {
        World world = event.getWorld();
        if (world.isRemote) return;

        ArrayList<CSpawnDefinition> spawnDefinitions = CSpawnDefinition.SPAWN_DEFINITIONS.computeIfAbsent((WorldServer) world, o -> new ArrayList<>());
        if (spawnDefinitions.size() == 0)
        {
            System.out.println(TextFormatting.AQUA + "Adding test spawn definitions");

            CSpawnDefinition spawnDefinition = new CSpawnDefinition();
            spawnDefinition.world = (WorldServer) world;


            CWeightedEntity weightedEntity = new CWeightedEntity();

            weightedEntity.entityNBT = JsonToNBT.getTagFromJson("{PersistenceRequired:1b,Attributes:[{Base:20.0d,Name:\"generic.maxHealth\"},{Base:0.0d,Modifiers:[{UUIDMost:5852893299261065939L,UUIDLeast:-9104073199294987705L,Amount:0.036573104176229713d,Operation:0,Name:\"Randomspawnbonus\"}],Name:\"generic.knockbackResistance\"},{Base:0.23000000417232513d,Name:\"generic.movementSpeed\"},{Base:2.0d,Name:\"generic.armor\"},{Base:0.0d,Name:\"generic.armorToughness\"},{Base:1.0d,Name:\"forge.swimSpeed\"},{Base:35.0d,Modifiers:[{UUIDMost:1016541881440161498L,UUIDLeast:-5301211752535089558L,Amount:0.05163393930534082d,Operation:1,Name:\"Randomspawnbonus\"}],Name:\"generic.followRange\"},{Base:3.0d,Name:\"generic.attackDamage\"},{Base:0.014275049799433781d,Name:\"zombie.spawnReinforcements\"}],Invulnerable:1b,HandDropChances:[0.085f,0.085f],id:\"minecraft:husk\",Health:20.0f,Air:300s,OnGround:1b,Rotation:[122.1131f,0.0f],HandItems:[{},{}],ArmorDropChances:[1.085f,0.085f,0.085f,0.085f],Pos:[1005.5d,65.0d,-1.5d],Fire:-1s,ArmorItems:[{},{},{},{}],}");

            CWeightedSpawnZone zone = new CWeightedSpawnZone();
            BlockPos pos = new BlockPos(1000, 200, 0);
            zone.setBounds(pos, pos);

            weightedEntity.weightedZones.add(zone);


            spawnDefinition.addWeightedEntity(weightedEntity);
            spawnDefinitions.add(spawnDefinition);
        }
    }
}
