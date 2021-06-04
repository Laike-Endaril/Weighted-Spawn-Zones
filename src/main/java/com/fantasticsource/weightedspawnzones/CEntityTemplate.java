package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.component.CStringUTF8;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.InputStream;
import java.io.OutputStream;

public class CEntityTemplate extends Component
{
    public String name = "", description = "";
    public NBTTagCompound compound = new NBTTagCompound();


    public CEntityTemplate()
    {
    }

    public CEntityTemplate(Entity target)
    {
        this("", "", target.serializeNBT());
    }

    public CEntityTemplate(String name, String description, NBTTagCompound compound)
    {
        this.name = name;
        this.description = description;
        this.compound = compound;
    }


    @Override
    public CEntityTemplate write(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, name);
        ByteBufUtils.writeUTF8String(buf, description);
        ByteBufUtils.writeUTF8String(buf, compound.toString());

        return this;
    }

    @Override
    public CEntityTemplate read(ByteBuf buf)
    {
        name = ByteBufUtils.readUTF8String(buf);
        description = ByteBufUtils.readUTF8String(buf);

        try
        {
            compound = JsonToNBT.getTagFromJson(ByteBufUtils.readUTF8String(buf));
        }
        catch (NBTException e)
        {
            compound = new NBTTagCompound();
            e.printStackTrace();
        }

        return this;
    }

    @Override
    public CEntityTemplate save(OutputStream stream)
    {
        CStringUTF8 cs = new CStringUTF8();

        cs.set(name).save(stream).set(description).save(stream).set(compound.toString()).save(stream);

        return this;
    }

    @Override
    public CEntityTemplate load(InputStream stream)
    {
        CStringUTF8 cs = new CStringUTF8();

        name = cs.load(stream).value;
        description = cs.load(stream).value;

        try
        {
            compound = JsonToNBT.getTagFromJson(cs.load(stream).value);
        }
        catch (NBTException e)
        {
            compound = new NBTTagCompound();
            e.printStackTrace();
        }

        return this;
    }
}
