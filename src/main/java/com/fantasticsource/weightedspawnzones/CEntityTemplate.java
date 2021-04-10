package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.tools.component.CInt;
import com.fantasticsource.tools.component.CStringUTF8;
import com.fantasticsource.tools.component.Component;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class CEntityTemplate extends Component
{
    protected String name = null;
    protected ArrayList<String> description = new ArrayList<>();
    protected NBTTagCompound compound = new NBTTagCompound();

    public CEntityTemplate()
    {
    }

    public CEntityTemplate(String name)
    {
        setName(name);
    }

    protected void setName(String name)
    {
        CEntityTemplate found = WeightedSpawnZones.ENTITY_DEFINITIONS.get(this.name);
        if (this == found)
        {
            found = WeightedSpawnZones.ENTITY_DEFINITIONS.get(name);
            if (found != null)
            {
                System.err.println(TextFormatting.RED + "Cannot rename entity definition, as name is already in use: " + name);
                return;
            }

            WeightedSpawnZones.ENTITY_DEFINITIONS.remove(this.name);
            this.name = name;
            WeightedSpawnZones.addEntityDefinition(this);
        }
        else this.name = name;
    }

    @Override
    public CEntityTemplate write(ByteBuf buf)
    {
        ByteBufUtils.writeUTF8String(buf, name);

        buf.writeInt(description.size());
        for (String s : description) ByteBufUtils.writeUTF8String(buf, s);

        ByteBufUtils.writeUTF8String(buf, compound.toString());

        return this;
    }

    @Override
    public CEntityTemplate read(ByteBuf buf)
    {
        name = ByteBufUtils.readUTF8String(buf);

        description.clear();
        for (int i = buf.readInt(); i > 0; i--) description.add(ByteBufUtils.readUTF8String(buf));

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

        cs.set(name).save(stream);

        new CInt().set(description.size()).save(stream);
        for (String s : description) cs.set(s).save(stream);

        cs.set(compound.toString()).save(stream);

        return this;
    }

    @Override
    public CEntityTemplate load(InputStream stream)
    {
        CStringUTF8 cs = new CStringUTF8();

        name = cs.load(stream).value;

        description.clear();
        for (int i = new CInt().load(stream).value; i > 0; i--) description.add(cs.load(stream).value);

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
