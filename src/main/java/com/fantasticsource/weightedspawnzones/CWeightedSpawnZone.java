package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.component.CZone;
import com.fantasticsource.tools.Tools;
import com.fantasticsource.tools.component.CBoolean;
import com.fantasticsource.tools.component.CInt;
import io.netty.buffer.ByteBuf;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class CWeightedSpawnZone extends CZone
{
    public int weight = 1;
    public boolean multiplyWeightByVolume = false;


    public CWeightedSpawnZone()
    {
    }


    public ArrayList<Long> getIntersectingChunks()
    {
        ArrayList<Long> result = new ArrayList<>();
        for (int x = min.getX() >> 4; x < max.getX() >> 4; x++)
        {
            for (int z = min.getZ() >> 4; z < max.getZ() >> 4; z++)
            {
                result.add(Tools.getLong(x, z));
            }
        }
        return result;
    }


    @Override
    public CWeightedSpawnZone write(ByteBuf buf)
    {
        super.write(buf);

        buf.writeInt(weight);
        buf.writeBoolean(multiplyWeightByVolume);

        return this;
    }

    @Override
    public CWeightedSpawnZone read(ByteBuf buf)
    {
        super.read(buf);

        weight = buf.readInt();
        multiplyWeightByVolume = buf.readBoolean();

        return this;
    }

    @Override
    public CWeightedSpawnZone save(OutputStream stream)
    {
        super.save(stream);

        new CInt().set(weight).save(stream);
        new CBoolean().set(multiplyWeightByVolume).save(stream);

        return this;
    }

    @Override
    public CWeightedSpawnZone load(InputStream stream)
    {
        super.load(stream);

        weight = new CInt().load(stream).value;
        multiplyWeightByVolume = new CBoolean().load(stream).value;

        return this;
    }
}
