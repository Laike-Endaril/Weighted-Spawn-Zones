package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.CStringUTF8;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.InputStream;
import java.io.OutputStream;

public class CWeightedEntity extends Component
{
    public NBTTagCompound entityNBT = new NBTTagCompound();
    public int weight = 1;
    public CWeightedSpawnZone queuedZone = null;
    public BlockPos queuedPos = null;
    public EntityLiving queuedEntity = null;


    @Override
    public CWeightedEntity write(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, entityNBT.toString());
        buf.writeInt(weight);

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

        return this;
    }

    @Override
    public CWeightedEntity save(OutputStream stream)
    {
        new CStringUTF8().set(entityNBT.toString()).save(stream);
        new CInt().set(weight).save(stream);

        return this;
    }

    @Override
    public CWeightedEntity load(InputStream stream)
    {
        try
        {
            entityNBT = JsonToNBT.getTagFromJson(new CStringUTF8().load(stream).value);
        }
        catch (NBTException e)
        {
            e.printStackTrace();
            return null;
        }

        weight = new CInt().load(stream).value;

        return this;
    }
}
