package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.MCTools;
import com.fantasticsource.mctools.gui.GUIScreen;
import com.fantasticsource.mctools.gui.element.text.GUIText;
import com.fantasticsource.mctools.gui.screen.MessageGUI;
import com.fantasticsource.mctools.gui.screen.TextSelectionGUI;
import com.fantasticsource.nbtmanipulator.NBTGUI;
import com.fantasticsource.weightedspawnzones.gui.SaveEntityTemplateGUI;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

import static com.fantasticsource.weightedspawnzones.WeightedSpawnZones.MODID;

public class Network
{
    public static final SimpleNetworkWrapper WRAPPER = new SimpleNetworkWrapper(MODID);
    private static int discriminator = 0;

    public static void init()
    {
        WRAPPER.registerMessage(NextEditedEntityTemplatePacketHandler.class, NextEditedEntityTemplatePacket.class, discriminator++, Side.CLIENT);
        WRAPPER.registerMessage(EditEntityTemplatePacketHandler.class, EditEntityTemplatePacket.class, discriminator++, Side.CLIENT);
        WRAPPER.registerMessage(SaveEntityTemplatePacketHandler.class, SaveEntityTemplatePacket.class, discriminator++, Side.SERVER);
        WRAPPER.registerMessage(RequestTemplateListPacketHandler.class, RequestTemplateListPacket.class, discriminator++, Side.SERVER);
        WRAPPER.registerMessage(TemplateListPacketHandler.class, TemplateListPacket.class, discriminator++, Side.CLIENT);
        WRAPPER.registerMessage(RequestLoadTemplateNBTPacketHandler.class, RequestLoadTemplateNBTPacket.class, discriminator++, Side.SERVER);
        WRAPPER.registerMessage(LoadTemplateNBTPacketHandler.class, LoadTemplateNBTPacket.class, discriminator++, Side.CLIENT);
    }


    public static class NextEditedEntityTemplatePacket implements IMessage
    {
        public CEntityTemplate template;

        public NextEditedEntityTemplatePacket()
        {
        }

        public NextEditedEntityTemplatePacket(CEntityTemplate template)
        {
            this.template = template;
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            template.write(buf);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            template = new CEntityTemplate().read(buf);
        }
    }

    public static class NextEditedEntityTemplatePacketHandler implements IMessageHandler<NextEditedEntityTemplatePacket, IMessage>
    {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(NextEditedEntityTemplatePacket packet, MessageContext ctx)
        {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                SaveEntityTemplateGUI.template = packet.template;
            });
            return null;
        }
    }


    public static class EditEntityTemplatePacket implements IMessage
    {
        public CEntityTemplate template;

        public EditEntityTemplatePacket()
        {
        }

        public EditEntityTemplatePacket(CEntityTemplate template)
        {
            this.template = template;
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            template.write(buf);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            template = new CEntityTemplate().read(buf);
        }
    }

    public static class EditEntityTemplatePacketHandler implements IMessageHandler<EditEntityTemplatePacket, IMessage>
    {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(EditEntityTemplatePacket packet, MessageContext ctx)
        {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                SaveEntityTemplateGUI.template = packet.template;
                new SaveEntityTemplateGUI();
            });
            return null;
        }
    }


    public static class SaveEntityTemplatePacket implements IMessage
    {
        CEntityTemplate template;

        public SaveEntityTemplatePacket()
        {
            //Required
        }

        public SaveEntityTemplatePacket(CEntityTemplate template)
        {
            this.template = template;
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            template.write(buf);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            template = new CEntityTemplate().read(buf);
        }
    }

    public static class SaveEntityTemplatePacketHandler implements IMessageHandler<SaveEntityTemplatePacket, IMessage>
    {
        @Override
        public IMessage onMessage(SaveEntityTemplatePacket packet, MessageContext ctx)
        {
            if (!MCTools.isOP(ctx.getServerHandler().player)) return null;

            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> WeightedSpawnZones.ENTITY_TEMPLATES.put(packet.template.name, packet.template));

            return null;
        }
    }


    public static class RequestTemplateListPacket implements IMessage
    {
        public RequestTemplateListPacket()
        {
            //Required
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
        }
    }

    public static class RequestTemplateListPacketHandler implements IMessageHandler<RequestTemplateListPacket, IMessage>
    {
        @Override
        public IMessage onMessage(RequestTemplateListPacket packet, MessageContext ctx)
        {
            if (!MCTools.isOP(ctx.getServerHandler().player)) return null;

            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> WRAPPER.sendTo(new TemplateListPacket(), ctx.getServerHandler().player));

            return null;
        }
    }


    public static class TemplateListPacket implements IMessage
    {
        ArrayList<String> templateNames;

        public TemplateListPacket()
        {
            templateNames = new ArrayList<>(WeightedSpawnZones.ENTITY_TEMPLATES.keySet());
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            buf.writeInt(templateNames.size());
            for (String templateName : templateNames) ByteBufUtils.writeUTF8String(buf, templateName);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            templateNames = new ArrayList<>();
            for (int i = buf.readInt(); i > 0; i--) templateNames.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    public static class TemplateListPacketHandler implements IMessageHandler<TemplateListPacket, IMessage>
    {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(TemplateListPacket packet, MessageContext ctx)
        {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                if (mc.currentScreen instanceof NBTGUI)
                {
                    if (packet.templateNames.size() == 0)
                    {
                        new MessageGUI("No Templates Saved", "There are currently no templates to load from");
                    }
                    else
                    {
                        GUIText textElement = new GUIText((GUIScreen) mc.currentScreen, "");
                        new TextSelectionGUI(textElement, "Select Template to Load", packet.templateNames.toArray(new String[0])).addOnClosedActions(() ->
                        {
                            String templateName = textElement.getText();
                            if (!templateName.equals("")) WRAPPER.sendToServer(new RequestLoadTemplateNBTPacket(templateName));
                        });
                    }
                }
            });
            return null;
        }
    }


    public static class RequestLoadTemplateNBTPacket implements IMessage
    {
        String templateName;

        public RequestLoadTemplateNBTPacket()
        {
            //Required
        }

        public RequestLoadTemplateNBTPacket(String templateName)
        {
            this.templateName = templateName;
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            ByteBufUtils.writeUTF8String(buf, templateName);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            templateName = ByteBufUtils.readUTF8String(buf);
        }
    }

    public static class RequestLoadTemplateNBTPacketHandler implements IMessageHandler<RequestLoadTemplateNBTPacket, IMessage>
    {
        @Override
        public IMessage onMessage(RequestLoadTemplateNBTPacket packet, MessageContext ctx)
        {
            if (!MCTools.isOP(ctx.getServerHandler().player)) return null;

            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() ->
            {
                CEntityTemplate template = WeightedSpawnZones.ENTITY_TEMPLATES.get(packet.templateName);
                if (template != null) WRAPPER.sendTo(new LoadTemplateNBTPacket(template), ctx.getServerHandler().player);
            });

            return null;
        }
    }


    public static class LoadTemplateNBTPacket implements IMessage
    {
        String nbtString;

        public LoadTemplateNBTPacket()
        {
        }

        public LoadTemplateNBTPacket(CEntityTemplate template)
        {
            nbtString = template.compound.toString();
        }

        @Override
        public void toBytes(ByteBuf buf)
        {
            ByteBufUtils.writeUTF8String(buf, nbtString);
        }

        @Override
        public void fromBytes(ByteBuf buf)
        {
            nbtString = ByteBufUtils.readUTF8String(buf);
        }
    }

    public static class LoadTemplateNBTPacketHandler implements IMessageHandler<LoadTemplateNBTPacket, IMessage>
    {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(LoadTemplateNBTPacket packet, MessageContext ctx)
        {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() ->
            {
                if (mc.currentScreen instanceof NBTGUI)
                {
                    ((NBTGUI) mc.currentScreen).code.setCode(MCTools.legibleNBT(packet.nbtString));
                }
            });
            return null;
        }
    }
}
