package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.weightedspawnzones.blocksanditems.BlocksAndItems;
import com.fantasticsource.weightedspawnzones.gui.NBTGUIHacks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

@Mod(modid = WeightedSpawnZones.MODID, name = WeightedSpawnZones.NAME, version = WeightedSpawnZones.VERSION, dependencies = "required-after:fantasticlib@[1.12.2.044zzf,);required-after:nbtmanipulator@[1.12.2.004g,)")
public class WeightedSpawnZones
{
    public static final String MODID = "weightedspawnzones";
    public static final String NAME = "Weighted Spawn Zones";
    public static final String VERSION = "1.12.2.000";

    protected static final File ENTITY_TEMPLATE_FILE = new File(MCTools.getConfigDir() + MODID + ".dat");
    public static final HashMap<String, CEntityTemplate> ENTITY_TEMPLATES = new HashMap<>();

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent event)
    {
        Network.init();

        MinecraftForge.EVENT_BUS.register(WeightedSpawnZones.class);
        MinecraftForge.EVENT_BUS.register(CSpawnDefinition.class);
        MinecraftForge.EVENT_BUS.register(BlocksAndItems.class);


        if (ENTITY_TEMPLATE_FILE.exists())
        {
            try
            {
                FileInputStream stream = new FileInputStream(ENTITY_TEMPLATE_FILE);
                for (int i = new CInt().load(stream).value; i > 0; i--)
                {
                    CEntityTemplate template = new CEntityTemplate().load(stream);
                    ENTITY_TEMPLATES.put(template.name, template);
                }
                stream.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }


        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT)
        {
            MinecraftForge.EVENT_BUS.register(NBTGUIHacks.class);
        }
    }

    protected static void saveEntityTemplates()
    {
        ENTITY_TEMPLATE_FILE.mkdirs();
        while (ENTITY_TEMPLATE_FILE.exists()) ENTITY_TEMPLATE_FILE.delete();
        try
        {
            FileOutputStream stream = new FileOutputStream(ENTITY_TEMPLATE_FILE);
            new CInt().set(ENTITY_TEMPLATES.size()).save(stream);
            for (CEntityTemplate template : ENTITY_TEMPLATES.values()) template.save(stream);
            stream.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
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
    public static void worldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();
        if (world.isRemote) return;

        ArrayList<CSpawnDefinition> spawnDefinitions = CSpawnDefinition.SPAWN_DEFINITIONS.computeIfAbsent((WorldServer) world, o -> new ArrayList<>());


        if (MCTools.devEnv() && spawnDefinitions.size() == 0 && ENTITY_TEMPLATES.size() > 0)
        {
            System.out.println(TextFormatting.AQUA + "Adding test spawn definitions");

            CSpawnDefinition spawnDefinition = new CSpawnDefinition();
            spawnDefinition.world = (WorldServer) world;


            CWeightedEntity weightedEntity = new CWeightedEntity();
            weightedEntity.templateName = ENTITY_TEMPLATES.containsKey("Test") ? "Test" : ENTITY_TEMPLATES.keySet().iterator().next();

            CWeightedSpawnZone zone = new CWeightedSpawnZone();
            BlockPos pos = new BlockPos(1000, 200, 0);
            zone.setBounds(pos, pos);

            weightedEntity.weightedZones.add(zone);


            spawnDefinition.addWeightedEntity(weightedEntity);
            spawnDefinitions.add(spawnDefinition);
        }
    }


    public static void editEntityTemplate(EntityPlayerMP player, CEntityTemplate template)
    {
        Network.WRAPPER.sendTo(new Network.EditEntityTemplatePacket(template), player);
    }
}
