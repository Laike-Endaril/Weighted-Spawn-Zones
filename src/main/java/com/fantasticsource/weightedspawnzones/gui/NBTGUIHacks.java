package com.fantasticsource.weightedspawnzones.gui;

import com.fantasticsource.mctools.ClientTickTimer;
import com.fantasticsource.mctools.gui.element.text.GUITextButton;
import com.fantasticsource.nbtmanipulator.NBTGUI;
import com.fantasticsource.tools.datastructures.Color;
import com.fantasticsource.weightedspawnzones.Network;
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


            gui.saveButton.internalText.setText("Save to Entity");


            //TODO implement templates in NBTManipulator instead, for entities, items, and TEs

            GUITextButton saveAsTemplate = new GUITextButton(gui, "Save to Template", Color.GREEN);
            saveAsTemplate.addClickActions(SaveEntityTemplateGUI::new);
            gui.root.add(gui.root.indexOf(gui.closeButton), saveAsTemplate);

            GUITextButton loadFromTemplate = new GUITextButton(gui, "Load from Template", Color.YELLOW);
            loadFromTemplate.addClickActions(() -> Network.WRAPPER.sendToServer(new Network.RequestTemplateListPacket()));
            gui.root.add(gui.root.indexOf(gui.closeButton), loadFromTemplate);
        });
    }
}
