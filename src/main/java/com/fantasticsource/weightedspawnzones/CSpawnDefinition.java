package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.ImprovedRayTracing;
import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.mctools.ServerTickTimer;
import com.fantasticsource.tools.Tools;
import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.CLong;
import com.fantasticsource.tools.component.Component;
import com.fantasticsource.tools.datastructures.Pair;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
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
    protected static final HashMap<WorldServer, ArrayList<CSpawnDefinition>> SPAWN_DEFINITIONS = new HashMap<>();
    protected static final HashMap<UUID, CWeightedEntity> UNFOUND_ENTITIES = new HashMap<>();


    protected ArrayList<CWeightedEntity> weightedEntities = new ArrayList<>(), spawnedEntities = new ArrayList<>();
    protected HashMap<Pair<Integer, Integer>, ArrayList<CWeightedEntity>> queuedAttempts = new HashMap<>();
    protected ArrayList<Long> intersectingChunks = new ArrayList<>();

    public int ticksBetweenAttempts = 20, limit = 10, destroyAfter = -1, minPlayerDistNoLOS = 16, minPlayerDistLOS = 64;


    public final int tickOffset = Tools.random(Integer.MAX_VALUE);
    protected WorldServer world;
    protected ArrayList<Chunk> loadedChunks = new ArrayList<>();


    public void addWeightedEntity(CWeightedEntity weightedEntity)
    {
        weightedEntities.add(weightedEntity);
        for (long l : weightedEntity.getIntersectingChunks())
        {
            if (!intersectingChunks.contains(l))
            {
                intersectingChunks.add(l);
                Pair<Integer, Integer> coords = Tools.getIntsFromLong(l);
                int x = coords.getKey(), z = coords.getValue();
                if (world.isBlockLoaded(new BlockPos(x, 0, z))) loadedChunks.add(world.getChunkFromChunkCoords(x, z));
            }
        }
    }

    public void removeWeightedEntity(CWeightedEntity weightedEntity)
    {
        if (weightedEntities.remove(weightedEntity))
        {
            recalcIntersectingChunks();
            return;
        }

        if (spawnedEntities.remove(weightedEntity))
        {
            recalcIntersectingChunks();
            return;
        }

        for (Map.Entry<Pair<Integer, Integer>, ArrayList<CWeightedEntity>> entry : queuedAttempts.entrySet())
        {
            if (entry.getValue().remove(weightedEntity))
            {
                recalcIntersectingChunks();
                if (entry.getValue().size() == 0) queuedAttempts.remove(entry.getKey());
                return;
            }
        }
    }


    protected void recalcIntersectingChunks()
    {
        intersectingChunks.clear();
        for (CWeightedEntity weightedEntity : weightedEntities)
        {
            for (long l : weightedEntity.getIntersectingChunks())
            {
                if (!intersectingChunks.contains(l))
                {
                    intersectingChunks.add(l);
                    Pair<Integer, Integer> coords = Tools.getIntsFromLong(l);
                    int x = coords.getKey(), z = coords.getValue();
                    if (world.isBlockLoaded(new BlockPos(x << 4, 0, z << 4))) loadedChunks.add(world.getChunkFromChunkCoords(x, z));
                }
            }
        }
        for (CWeightedEntity weightedEntity : spawnedEntities)
        {
            for (long l : weightedEntity.getIntersectingChunks())
            {
                if (!intersectingChunks.contains(l))
                {
                    intersectingChunks.add(l);
                    Pair<Integer, Integer> coords = Tools.getIntsFromLong(l);
                    int x = coords.getKey(), z = coords.getValue();
                    if (world.isBlockLoaded(new BlockPos(x << 4, 0, z << 4))) loadedChunks.add(world.getChunkFromChunkCoords(x, z));
                }
            }
        }
        for (Pair<Integer, Integer> coords : queuedAttempts.keySet())
        {
            int x = coords.getKey(), z = coords.getValue();
            long l = Tools.getLong(x, z);
            if (!intersectingChunks.contains(l))
            {
                intersectingChunks.add(l);
                if (world.isBlockLoaded(new BlockPos(x << 4, 0, z << 4))) loadedChunks.add(world.getChunkFromChunkCoords(x, z));
            }
        }
    }

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event)
    {
        World world = event.getWorld();
        if (world.isRemote) return;

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
                    spawnDefinition.world = (WorldServer) world;
                    spawnDefinitions.add(spawnDefinition);
                    for (CWeightedEntity weightedEntity : spawnDefinition.spawnedEntities) UNFOUND_ENTITIES.put(weightedEntity.entityID, weightedEntity);
                }
                SPAWN_DEFINITIONS.put((WorldServer) world, spawnDefinitions);
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
        if (world.isRemote) return;

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
        World world = event.getWorld();
        if (world.isRemote) return;

        SPAWN_DEFINITIONS.remove(world);
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
        if (weightedEntity != null)
        {
            weightedEntity.entity = entity;
        }
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END) return;

        long tick = ServerTickTimer.currentTick();
        for (ArrayList<CSpawnDefinition> spawnDefinitions : SPAWN_DEFINITIONS.values())
        {
            for (CSpawnDefinition spawnDefinition : spawnDefinitions)
            {
                spawnDefinition.resetDeadEntities();
                if ((tick + spawnDefinition.tickOffset) % spawnDefinition.ticksBetweenAttempts == 0) spawnDefinition.tryFillSpawns();
            }
        }
    }


    protected void resetDeadEntities()
    {
        for (CWeightedEntity weightedEntity : spawnedEntities.toArray(new CWeightedEntity[0]))
        {
            Entity entity = weightedEntity.entity;
            if (entity == null) continue;

            if (!MCTools.entityIsValid(entity))
            {
                weightedEntity.entity = null;

                if (entity.isDead)
                {
                    spawnedEntities.remove(weightedEntity);
                    weightedEntity.entityID = null;
                    weightedEntities.add(weightedEntity);
                }
                else UNFOUND_ENTITIES.put(entity.getUniqueID(), weightedEntity);
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
            for (CWeightedEntity weightedEntity : queued)
            {
                if (!trySpawn(weightedEntity)) weightedEntities.add(weightedEntity);
            }
        }

        tryFillSpawns();
    }

    protected void tryFillSpawns()
    {
        if (world != null && spawnedEntities.size() + queuedAttempts.size() < limit)
        {
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
    }

    /**
     * @return true if an entity was spawned or if an entity spawn attempt was queued
     */
    protected boolean trySpawn(CWeightedEntity weightedEntity)
    {
        BlockPos pos = weightedEntity.queuedPos;
        if (pos == null) pos = queueRandomPosition(weightedEntity);

        if (!world.isBlockLoaded(pos))
        {
            weightedEntities.remove(weightedEntity);
            queuedAttempts.computeIfAbsent(new Pair<>(pos.getX() >> 4, pos.getZ() >> 4), o -> new ArrayList<>()).add(weightedEntity);
            return true;
        }


        if (trySpawnInternal(weightedEntity))
        {
            weightedEntities.remove(weightedEntity);
            return true;
        }


        weightedEntity.entity = null;
        weightedEntity.queuedZone = null;
        weightedEntity.queuedPos = null;
        return false;
    }

    protected boolean trySpawnInternal(CWeightedEntity weightedEntity)
    {
        Entity entity = weightedEntity.entity;
        if (entity == null) entity = EntityList.createEntityFromNBT(WeightedSpawnZones.ENTITY_DEFINITIONS.get(weightedEntity.entityDefName).compound, world);
        if (entity == null) return false;


        BlockPos pos = weightedEntity.queuedPos;
        int y = pos.getY(), maxY = weightedEntity.queuedZone.getMax().getY();
        float x = pos.getX() + 0.5f, z = pos.getZ() + 0.5f;
        int yStart = pos.getY();
        do
        {
            boolean canSpawn = !(entity instanceof EntityLiving) || ForgeEventFactory.canEntitySpawn((EntityLiving) entity, world, x, y, z, false) != Event.Result.DENY;
            if (canSpawn)
            {
                ArrayList<Vec3d> playerVecs = new ArrayList<>();
                Vec3d vec = new Vec3d(x, y + entity.height * 0.5, z), vec2;
                int reqDistNoLOSSquared = minPlayerDistNoLOS * minPlayerDistNoLOS, reqDistLOSSquared = minPlayerDistLOS * minPlayerDistLOS;
                double distSquared;
                for (EntityPlayer player : world.playerEntities)
                {
                    vec2 = new Vec3d(player.posX, player.posY + player.eyeHeight, player.posZ);
                    distSquared = vec.squareDistanceTo(vec2);
                    if (distSquared < reqDistNoLOSSquared)
                    {
                        canSpawn = false;
                        break;
                    }
                    if (distSquared < reqDistLOSSquared) playerVecs.add(vec2);
                }

                if (canSpawn)
                {
                    for (Vec3d vec3 : playerVecs)
                    {
                        if (ImprovedRayTracing.isUnobstructed(world, vec, vec3, false))
                        {
                            canSpawn = false;
                            break;
                        }
                    }
                }

                if (canSpawn)
                {
                    weightedEntity.entity = entity;
                    weightedEntity.entityID = entity.getUniqueID();

                    Pair<Integer, Integer> pair = new Pair<>((int) x >> 4, (int) z >> 4);
                    ArrayList<CWeightedEntity> list = queuedAttempts.get(pair);
                    if (list != null)
                    {
                        list.remove(weightedEntity);
                        if (list.size() == 0) queuedAttempts.remove(pair);
                    }

                    spawnedEntities.add(weightedEntity);

                    entity.setPosition(x, y, z);
                    world.spawnEntity(entity);

                    if (destroyAfter > 0 && --destroyAfter == 0)
                    {
                        ArrayList<CSpawnDefinition> list2 = SPAWN_DEFINITIONS.get(world);
                        if (list2 != null)
                        {
                            list2.remove(this);
                            if (list2.size() == 0) SPAWN_DEFINITIONS.remove(world);
                        }
                    }

                    return true;
                }
            }

            if (++y > maxY) y = weightedEntity.queuedZone.getMin().getY();
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
        buf.writeInt(destroyAfter);
        buf.writeInt(minPlayerDistNoLOS);
        buf.writeInt(minPlayerDistLOS);

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
        destroyAfter = buf.readInt();
        minPlayerDistNoLOS = buf.readInt();
        minPlayerDistLOS = buf.readInt();

        return this;
    }

    @Override
    public CSpawnDefinition save(OutputStream stream)
    {
        CInt ci = new CInt();
        CLong cl = new CLong();

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

        ci.set(limit).save(stream).set(destroyAfter).save(stream).set(minPlayerDistNoLOS).save(stream).set(minPlayerDistLOS).save(stream);

        return this;
    }

    @Override
    public CSpawnDefinition load(InputStream stream)
    {
        CInt ci = new CInt();
        CLong cl = new CLong();

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
        destroyAfter = ci.load(stream).value;
        minPlayerDistNoLOS = ci.load(stream).value;
        minPlayerDistLOS = ci.load(stream).value;

        return this;
    }
}
