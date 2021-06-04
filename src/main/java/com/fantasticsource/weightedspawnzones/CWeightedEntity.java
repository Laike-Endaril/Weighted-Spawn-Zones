package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.component.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class CWeightedEntity extends Component
{
    public String templateName = null;
    public int weight = 1;
    public ArrayList<CWeightedSpawnZone> weightedZones = new ArrayList<>();
    public UUID entityID = null;

    public CWeightedSpawnZone queuedZone = null;
    public BlockPos queuedPos = null;
    public Entity entity = null;


    public ArrayList<Long> getIntersectingChunks()
    {
        ArrayList<Long> result = new ArrayList<>();
        for (CWeightedSpawnZone zone : weightedZones)
        {
            result.addAll(zone.getIntersectingChunks());
        }
        return result;
    }


    @Override
    public CWeightedEntity write(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, templateName);
        buf.writeInt(weight);

        buf.writeInt(weightedZones.size());
        for (CWeightedSpawnZone weightedSpawnZone : weightedZones) weightedSpawnZone.write(buf);

        buf.writeBoolean(entityID != null);
        if (entityID != null) new CUUID().set(entityID).write(buf);

        return this;
    }

    @Override
    public CWeightedEntity read(ByteBuf buf)
    {
        templateName = ByteBufUtils.readUTF8String(buf);
        weight = buf.readInt();

        weightedZones.clear();
        for (int i = buf.readInt(); i > 0; i--) weightedZones.add(new CWeightedSpawnZone().read(buf));

        if (buf.readBoolean()) entityID = new CUUID().read(buf).value;

        return this;
    }

    @Override
    public CWeightedEntity save(OutputStream stream)
    {
        new CStringUTF8().set(templateName).save(stream);
        new CInt().set(weight).save(stream).set(weightedZones.size()).save(stream);
        for (CWeightedSpawnZone weightedSpawnZone : weightedZones) weightedSpawnZone.save(stream);

        new CBoolean().set(entityID != null).save(stream);
        if (entityID != null) new CUUID().set(entityID).save(stream);

        return this;
    }

    @Override
    public CWeightedEntity load(InputStream stream)
    {
        CInt ci = new CInt();

        templateName = new CStringUTF8().load(stream).value;
        weight = ci.load(stream).value;

        weightedZones.clear();
        for (int i = ci.load(stream).value; i > 0; i--) weightedZones.add(new CWeightedSpawnZone().load(stream));

        if (new CBoolean().load(stream).value) entityID = new CUUID().load(stream).value;

        return this;
    }
}
