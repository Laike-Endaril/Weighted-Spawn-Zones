package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.Tools;
import com.fantasticsource.tools.component.CBoolean;
import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class CSpawnDefinition extends Component
{
    public World world;
    public HashSet<CWeightedSpawnZone> weightedZones = new HashSet<>();
    public HashSet<CWeightedEntity> weightedEntities = new HashSet<>(), queuedAttempts = new HashSet<>(), failedSpawns = new HashSet<>();
    public LinkedHashMap<UUID, CWeightedEntity> spawnedEntities = new LinkedHashMap<>();
    public HashSet<Long> intersectingChunks = new HashSet<>();
    public HashMap<CWeightedEntity, Integer> failedAttemptCounts = new HashMap<>();

    public boolean spawnWithinPlayerLOS = false;
    public int sameEntityAttempts = 10, limit = 10;


    public void chunkLoad(ChunkEvent.Load event)
    {
        Chunk chunk = event.getChunk();
        if (!intersectingChunks.contains(Tools.getLong(chunk.x, chunk.z))) return;


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


        weightedEntity.queuedEntity = null;
        weightedEntity.queuedZone = null;
        weightedEntity.queuedPos = null;
        queuedAttempts.remove(weightedEntity);
        failedSpawns.add(weightedEntity);
    }

    protected boolean trySpawnInternal(CWeightedEntity weightedEntity)
    {
        EntityLiving entity = weightedEntity.queuedEntity;
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
                weightedEntity.queuedEntity = null;
                queuedAttempts.remove(weightedEntity);
                spawnedEntities.put(entity.getPersistentID(), weightedEntity);

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
