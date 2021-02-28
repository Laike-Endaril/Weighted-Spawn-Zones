package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.CStringUTF8;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class CWeightedEntity extends Component
{
    public NBTTagCompound entityNBT = new NBTTagCompound();
    public int weight = 1;
    public ArrayList<CWeightedSpawnZone> weightedZones = new ArrayList<>();

    public CWeightedSpawnZone queuedZone = null;
    public BlockPos queuedPos = null;
    public EntityLiving entity = null;
    public UUID entityID = null;


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
        ByteBufUtils.writeUTF8String(buf, entityNBT.toString());
        buf.writeInt(weight);

        buf.writeInt(weightedZones.size());
        for (CWeightedSpawnZone weightedSpawnZone : weightedZones) weightedSpawnZone.write(buf);

        return this;
    }

    @Override
    public CWeightedEntity read(ByteBuf buf)
    {
        try
        {
            entityNBT = JsonToNBT.getTagFromJson(ByteBufUtils.readUTF8String(buf));
        }
        catch (NBTException e)
        {
            e.printStackTrace();
            return null;
        }

        weight = buf.readInt();

        weightedZones.clear();
        for (int i = buf.readInt(); i > 0; i--) weightedZones.add(new CWeightedSpawnZone().read(buf));

        return this;
    }

    @Override
    public CWeightedEntity save(OutputStream stream)
    {
        new CStringUTF8().set(entityNBT.toString()).save(stream);
        new CInt().set(weight).save(stream).set(weightedZones.size()).save(stream);
        for (CWeightedSpawnZone weightedSpawnZone : weightedZones) weightedSpawnZone.save(stream);

        return this;
    }

    @Override
    public CWeightedEntity load(InputStream stream)
    {
        CInt ci = new CInt();

        try
        {
            entityNBT = JsonToNBT.getTagFromJson(new CStringUTF8().load(stream).value);
        }
        catch (NBTException e)
        {
            e.printStackTrace();
            return null;
        }

        weight = ci.load(stream).value;

        weightedZones.clear();
        for (int i = ci.load(stream).value; i > 0; i--) weightedZones.add(new CWeightedSpawnZone().load(stream));

        return this;
    }
}
