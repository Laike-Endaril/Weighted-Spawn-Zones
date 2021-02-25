package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.Tools;
import com.fantasticsource.tools.component.CBoolean;
import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import static com.fantasticsource.weightedspawnzones.WeightedSpawnZones.MODID;

public class CSpawnDefinition extends Component
{
    protected static final HashMap<World, ArrayList<CSpawnDefinition>> SPAWN_DEFINITIONS = new HashMap<>();
    protected static final HashMap<UUID, CWeightedEntity> UNFOUND_ENTITIES = new HashMap<>();

    public World world;
    public HashSet<CWeightedSpawnZone> weightedZones = new HashSet<>();
    public HashSet<CWeightedEntity> weightedEntities = new HashSet<>(), queuedAttempts = new HashSet<>(), failedSpawns = new HashSet<>();
    public HashSet<Long> intersectingChunks = new HashSet<>();
    public HashMap<CWeightedEntity, Integer> failedAttemptCounts = new HashMap<>();

    public ArrayList<CWeightedEntity> spawnedEntities = new ArrayList<>();
    protected ArrayList<Chunk> loadedChunks = new ArrayList<>();

    public boolean spawnWithinPlayerLOS = false;
    public int sameEntityAttempts = 10, limit = 10;


    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();
        File file = new File(world.getSaveHandler().getWorldDirectory() + File.separator + MODID + ".dat");
        if (file.exists())
        {
            try
            {
                FileInputStream stream = new FileInputStream(file);
                ArrayList<CSpawnDefinition> spawnDefinitions = new ArrayList<>();
                for (int i = new CInt().load(stream).value; i > 0; i--)
                {
                    CSpawnDefinition spawnDefinition = new CSpawnDefinition().load(stream);
                    spawnDefinitions.add(spawnDefinition);
                    for (CWeightedEntity weightedEntity : spawnDefinition.spawnedEntities) UNFOUND_ENTITIES.put(weightedEntity.entityID, weightedEntity);
                }
                SPAWN_DEFINITIONS.put(world, spawnDefinitions);
                stream.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void worldSave(WorldEvent.Save event)
    {
        //This event happens *after* the world has saved ie. not good for setting values to be saved by vanilla code

        World world = event.getWorld();
        File file = new File(world.getSaveHandler().getWorldDirectory() + File.separator + MODID + ".dat");

        ArrayList<CSpawnDefinition> spawnDefinitions = SPAWN_DEFINITIONS.get(world);
        if (spawnDefinitions == null || spawnDefinitions.size() == 0)
        {
            while (file.exists()) file.delete();
            return;
        }

        if (world.getSaveHandler().getWorldDirectory().exists())
        {
            try
            {
                FileOutputStream stream = new FileOutputStream(file);
                new CInt().set(spawnDefinitions.size()).save(stream);
                for (CSpawnDefinition spawnDefinition : spawnDefinitions) spawnDefinition.save(stream);
                stream.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event)
    {
        SPAWN_DEFINITIONS.remove(event.getWorld());
    }

    @SubscribeEvent
    public static void chunkLoad(ChunkEvent.Load event)
    {
        if (event.getWorld().isRemote) return;

        ArrayList<CSpawnDefinition> spawnDefinitions = SPAWN_DEFINITIONS.get(event.getWorld());
        if (spawnDefinitions == null) return;

        for (CSpawnDefinition spawnDefinition : spawnDefinitions) spawnDefinition.addChunk(event.getChunk());
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event)
    {
        if (event.getWorld().isRemote) return;

        ArrayList<CSpawnDefinition> spawnDefinitions = SPAWN_DEFINITIONS.get(event.getWorld());
        if (spawnDefinitions == null) return;

        for (CSpawnDefinition spawnDefinition : spawnDefinitions) spawnDefinition.loadedChunks.remove(event.getChunk());
    }

    @SubscribeEvent
    public static void entityJoinWorld(EntityJoinWorldEvent event)
    {
        Entity entity = event.getEntity();
        World world = entity.world;
        if (world.isRemote || !(entity instanceof EntityLiving)) return;

        CWeightedEntity weightedEntity = UNFOUND_ENTITIES.remove(entity.getUniqueID());
        if (weightedEntity != null) weightedEntity.entity = (EntityLiving) entity;
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event)
    {
        for (ArrayList<CSpawnDefinition> spawnDefinitions : SPAWN_DEFINITIONS.values())
        {
            for (CSpawnDefinition spawnDefinition : spawnDefinitions) spawnDefinition.removeInvalidEntities();
        }
    }


    protected void removeInvalidEntities()
    {
        for (CWeightedEntity weightedEntity : spawnedEntities.toArray(new CWeightedEntity[0]))
        {
            if (weightedEntity.entity == null) weightedEntity.entity = (EntityLiving) FMLCommonHandler.instance().getMinecraftServerInstance().getEntityFromUuid(weightedEntity.entityID);

            Entity entity = weightedEntity.entity;
            if (entity != null && !MCTools.entityIsValid(entity))
            {
                spawnedEntities.remove(weightedEntity);
                weightedEntity.entityID = null;
                weightedEntity.entity = null;
                weightedEntities.add(weightedEntity);
            }
        }
    }

    protected void addChunk(Chunk chunk)
    {
        if (!intersectingChunks.contains(Tools.getLong(chunk.x, chunk.z))) return;


        loadedChunks.add(chunk);


        for (CWeightedEntity weightedEntity : queuedAttempts)
        {
            if (world.isBlockLoaded(weightedEntity.queuedPos)) trySpawn(weightedEntity);
        }


        ArrayList<CWeightedEntity> list = new ArrayList<>();
        for (CWeightedEntity weightedEntity : weightedEntities)
        {
            for (int i = weightedEntity.weight; i > 0; i--) list.add(weightedEntity);
        }
        while (list.size() > 0 && spawnedEntities.size() + queuedAttempts.size() < limit)
        {
            CWeightedEntity weightedEntity = Tools.choose(list);
            while (list.remove(weightedEntity)) ;
            trySpawn(weightedEntity);
        }
    }

    protected void trySpawn(CWeightedEntity weightedEntity)
    {
        weightedEntities.remove(weightedEntity);

        BlockPos pos = weightedEntity.queuedPos;
        if (pos == null) pos = queueRandomPosition(weightedEntity);

        for (int i = failedAttemptCounts.getOrDefault(weightedEntity, 0); i < sameEntityAttempts; i++)
        {
            if (!world.isBlockLoaded(pos))
            {
                if (i > 0) failedAttemptCounts.put(weightedEntity, i);
                queuedAttempts.add(weightedEntity);
                return;
            }


            if (trySpawnInternal(weightedEntity)) return;


            pos = queueRandomPosition(weightedEntity);
        }


        weightedEntity.entity = null;
        weightedEntity.queuedZone = null;
        weightedEntity.queuedPos = null;
        queuedAttempts.remove(weightedEntity);
        failedSpawns.add(weightedEntity);
    }

    protected boolean trySpawnInternal(CWeightedEntity weightedEntity)
    {
        EntityLiving entity = weightedEntity.entity;
        if (entity == null) entity = (EntityLiving) EntityList.createEntityFromNBT(weightedEntity.entityNBT, world);
        if (entity == null) return false;


        BlockPos pos = weightedEntity.queuedPos;
        int y = pos.getY(), maxY = weightedEntity.queuedZone.getMax().getY();
        float x = pos.getX() + 0.5f, z = pos.getZ() + 0.5f;
        int yStart = pos.getY();
        do
        {
            if (ForgeEventFactory.canEntitySpawn(entity, world, x, y++, z, false) != Event.Result.DENY)
            {
                weightedEntity.entity = null;
                queuedAttempts.remove(weightedEntity);
                spawnedEntities.add(weightedEntity);

                entity.setPosition(x, y, z);
                world.spawnEntity(entity);

                return true;
            }

            if (y > maxY) y = weightedEntity.queuedZone.getMin().getY();
        }
        while (y != yStart);


        return false;
    }

    protected BlockPos queueRandomPosition(CWeightedEntity weightedEntity)
    {
        ArrayList<CWeightedSpawnZone> list = new ArrayList<>();
        for (CWeightedSpawnZone zone : weightedZones)
        {
            for (int i = zone.weight; i > 0; i--) list.add(zone);
        }
        CWeightedSpawnZone zone = Tools.choose(list);
        weightedEntity.queuedZone = zone;

        BlockPos min = zone.getMin(), max = zone.getMax();
        weightedEntity.queuedPos = new BlockPos(min.getX() + Tools.random(max.getX() - min.getX()), min.getY() + Tools.random(max.getY() - min.getY()), min.getZ() + Tools.random(max.getZ() - min.getZ()));
        return weightedEntity.queuedPos;
    }


    @Override
    public CSpawnDefinition write(ByteBuf buf)
    {
        buf.writeInt(weightedZones.size());
        for (CWeightedSpawnZone zone : weightedZones) zone.write(buf);

        buf.writeInt(weightedEntities.size());
        for (CWeightedEntity entity : weightedEntities) entity.write(buf);

        buf.writeBoolean(spawnWithinPlayerLOS);

        return this;
    }

    @Override
    public CSpawnDefinition read(ByteBuf buf)
    {
        weightedZones.clear();
        for (int i = buf.readInt(); i > 0; i--) weightedZones.add(new CWeightedSpawnZone().read(buf));

        weightedEntities.clear();
        for (int i = buf.readInt(); i > 0; i--) weightedEntities.add(new CWeightedEntity().read(buf));

        spawnWithinPlayerLOS = buf.readBoolean();

        return this;
    }

    @Override
    public CSpawnDefinition save(OutputStream stream)
    {
        CInt ci = new CInt();

        ci.set(weightedZones.size()).save(stream);
        for (CWeightedSpawnZone zone : weightedZones) zone.save(stream);

        ci.set(weightedEntities.size()).save(stream);
        for (CWeightedEntity entity : weightedEntities) entity.save(stream);

        new CBoolean().set(spawnWithinPlayerLOS).save(stream);

        return this;
    }

    @Override
    public CSpawnDefinition load(InputStream stream)
    {
        CInt ci = new CInt();
        CBoolean cb = new CBoolean();

        weightedZones.clear();
        for (int i = ci.load(stream).value; i > 0; i--) weightedZones.add(new CWeightedSpawnZone().load(stream));

        weightedEntities.clear();
        for (int i = ci.load(stream).value; i > 0; i--) weightedEntities.add(new CWeightedEntity().load(stream));

        spawnWithinPlayerLOS = cb.load(stream).value;

        return this;
    }
}
