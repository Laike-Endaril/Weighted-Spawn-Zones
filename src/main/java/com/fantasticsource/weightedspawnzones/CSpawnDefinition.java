package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.tools.Tools;
import com.fantasticsource.tools.component.CBoolean;
import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.CLong;
import com.fantasticsource.tools.component.Component;
import com.fantasticsource.tools.datastructures.Pair;
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
import java.util.Map;
import java.util.UUID;

import static com.fantasticsource.weightedspawnzones.WeightedSpawnZones.MODID;

public class CSpawnDefinition extends Component
{
    protected static final HashMap<World, ArrayList<CSpawnDefinition>> SPAWN_DEFINITIONS = new HashMap<>();
    protected static final HashMap<UUID, CWeightedEntity> UNFOUND_ENTITIES = new HashMap<>();


    protected ArrayList<CWeightedEntity> weightedEntities = new ArrayList<>(), spawnedEntities = new ArrayList<>();
    protected ArrayList<Long> intersectingChunks = new ArrayList<>();
    protected HashMap<Pair<Integer, Integer>, ArrayList<CWeightedEntity>> queuedAttempts = new HashMap<>();

    public int limit = 10;
    public boolean spawnWithinPlayerLOS = false;


    protected World world;
    protected ArrayList<Chunk> loadedChunks = new ArrayList<>();


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


        ArrayList<CWeightedEntity> queued = queuedAttempts.remove(new Pair<>(chunk.x, chunk.z));
        if (queued != null)
        {
            for (CWeightedEntity weightedEntity : queued) trySpawn(weightedEntity);
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

        if (!world.isBlockLoaded(pos))
        {
            queuedAttempts.computeIfAbsent(new Pair<>(pos.getX() >> 4, pos.getZ() >> 4), o -> new ArrayList<>()).add(weightedEntity);
            return;
        }


        if (trySpawnInternal(weightedEntity)) return;


        weightedEntity.entity = null;
        weightedEntity.queuedZone = null;
        weightedEntity.queuedPos = null;
        queuedAttempts.remove(weightedEntity);
        weightedEntities.add(weightedEntity);
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
        for (CWeightedSpawnZone zone : weightedEntity.weightedZones)
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
        buf.writeInt(weightedEntities.size());
        for (CWeightedEntity entity : weightedEntities) entity.write(buf);

        buf.writeInt(spawnedEntities.size());
        for (CWeightedEntity weightedEntity : spawnedEntities) weightedEntity.write(buf);

        buf.writeInt(intersectingChunks.size());
        for (long l : intersectingChunks) buf.writeLong(l);

        buf.writeInt(queuedAttempts.size());
        for (Map.Entry<Pair<Integer, Integer>, ArrayList<CWeightedEntity>> entry : queuedAttempts.entrySet())
        {
            buf.writeInt(entry.getKey().getKey());
            buf.writeInt(entry.getKey().getValue());
            buf.writeInt(entry.getValue().size());
            for (CWeightedEntity weightedEntity : entry.getValue()) weightedEntity.write(buf);
        }

        buf.writeInt(limit);
        buf.writeBoolean(spawnWithinPlayerLOS);

        return this;
    }

    @Override
    public CSpawnDefinition read(ByteBuf buf)
    {
        weightedEntities.clear();
        for (int i = buf.readInt(); i > 0; i--) weightedEntities.add(new CWeightedEntity().read(buf));

        spawnedEntities.clear();
        for (int i = buf.readInt(); i > 0; i--) spawnedEntities.add(new CWeightedEntity().read(buf));

        intersectingChunks.clear();
        for (int i = buf.readInt(); i > 0; i--) intersectingChunks.add(buf.readLong());

        queuedAttempts.clear();
        for (int i = buf.readInt(); i > 0; i--)
        {
            ArrayList<CWeightedEntity> list = new ArrayList<>();
            queuedAttempts.put(new Pair<>(buf.readInt(), buf.readInt()), list);
            for (int i2 = buf.readInt(); i2 > 0; i2--) list.add(new CWeightedEntity().read(buf));
        }

        limit = buf.readInt();
        spawnWithinPlayerLOS = buf.readBoolean();

        return this;
    }

    @Override
    public CSpawnDefinition save(OutputStream stream)
    {
        CInt ci = new CInt();
        CLong cl = new CLong();
        CBoolean cb = new CBoolean();

        ci.set(weightedEntities.size()).save(stream);
        for (CWeightedEntity entity : weightedEntities) entity.save(stream);

        ci.set(spawnedEntities.size()).save(stream);
        for (CWeightedEntity entity : spawnedEntities) entity.save(stream);

        ci.set(intersectingChunks.size()).save(stream);
        for (long l : intersectingChunks) cl.set(l).save(stream);

        ci.set(queuedAttempts.size()).save(stream);
        for (Map.Entry<Pair<Integer, Integer>, ArrayList<CWeightedEntity>> entry : queuedAttempts.entrySet())
        {
            ci.set(entry.getKey().getKey()).save(stream).set(entry.getKey().getValue()).save(stream).set(entry.getValue().size()).save(stream);
            for (CWeightedEntity weightedEntity : entry.getValue()) weightedEntity.save(stream);
        }

        ci.set(limit).save(stream);
        cb.set(spawnWithinPlayerLOS).save(stream);

        return this;
    }

    @Override
    public CSpawnDefinition load(InputStream stream)
    {
        CInt ci = new CInt();
        CLong cl = new CLong();
        CBoolean cb = new CBoolean();

        weightedEntities.clear();
        for (int i = ci.load(stream).value; i > 0; i--) weightedEntities.add(new CWeightedEntity().load(stream));

        spawnedEntities.clear();
        for (int i = ci.load(stream).value; i > 0; i--) spawnedEntities.add(new CWeightedEntity().load(stream));

        intersectingChunks.clear();
        for (int i = ci.load(stream).value; i > 0; i--) intersectingChunks.add(cl.load(stream).value);

        queuedAttempts.clear();
        for (int i = ci.load(stream).value; i > 0; i--)
        {
            ArrayList<CWeightedEntity> list = new ArrayList<>();
            queuedAttempts.put(new Pair<>(ci.load(stream).value, ci.load(stream).value), list);
            for (int i2 = ci.load(stream).value; i2 > 0; i2--) list.add(new CWeightedEntity().load(stream));
        }

        limit = ci.load(stream).value;
        spawnWithinPlayerLOS = cb.load(stream).value;

        return this;
    }
}
