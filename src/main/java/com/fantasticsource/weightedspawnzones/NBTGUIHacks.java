package com.fantasticsource.weightedspawnzones;

import com.fantasticsource.mctools.ClientTickTimer;
import com.fantasticsource.mctools.gui.element.text.GUITextButton;
import com.fantasticsource.nbtmanipulator.NBTGUI;
import com.fantasticsource.tools.datastructures.Color;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NBTGUIHacks
{
    @SubscribeEvent
    public static void guiPostInit(GuiScreenEvent.InitGuiEvent.Post event)
    {
        if (!(event.getGui() instanceof NBTGUI)) return;


        ClientTickTimer.schedule(1, () ->
        {
            NBTGUI gui = (NBTGUI) event.getGui();
            GUITextButton saveAsTemplate = new GUITextButton(gui, "Save as Template", Color.GREEN);
            gui.root.add(gui.root.indexOf(gui.closeButton), saveAsTemplate);
        });
    }
}
