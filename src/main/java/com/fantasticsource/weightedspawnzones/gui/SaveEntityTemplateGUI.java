package com.fantasticsource.weightedspawnzones.gui;

import com.fantasticsource.mctools.gui.GUIScreen;
import com.fantasticsource.mctools.gui.element.GUIElement;
import com.fantasticsource.mctools.gui.element.other.GUIDarkenedBackground;
import com.fantasticsource.mctools.gui.element.text.*;
import com.fantasticsource.mctools.gui.element.text.filter.FilterNone;
import com.fantasticsource.mctools.gui.element.text.filter.FilterNotEmpty;
import com.fantasticsource.tools.datastructures.Color;
import com.fantasticsource.weightedspawnzones.CEntityTemplate;
import com.fantasticsource.weightedspawnzones.Network;
import net.minecraft.util.text.TextFormatting;

public class SaveEntityTemplateGUI extends GUIScreen
{
    public static CEntityTemplate template;

    public SaveEntityTemplateGUI()
    {
        show();
        root.addAll(
                new GUIDarkenedBackground(this),
                new GUINavbar(this)
        );


        GUITextButton save = new GUITextButton(this, "Save", Color.GREEN);
        root.addAll(
                save,
                new GUITextButton(this, "Cancel", Color.RED).addClickActions(this::close)
        );


        GUILabeledTextInput name = new GUILabeledTextInput(this, " Name: ", template.name, FilterNotEmpty.INSTANCE);
        root.addAll(new GUITextSpacer(this), name);


        GUIMultilineTextInput description = new GUIMultilineTextInput(this, template.description, FilterNone.INSTANCE);
        GUIText descriptionLabel = new GUIText(this, TextFormatting.GOLD + " Description...");
        descriptionLabel.addClickActions(() -> description.setActive(true));
        root.addAll(new GUITextSpacer(this), descriptionLabel, new GUIElement(this, 1, 0), description);


        //Add actions
        save.addClickActions(() ->
        {
            //Validation
            if (!name.valid()) return;


            //Processing
            template.name = name.getText();
            template.description = description.getText();


            Network.WRAPPER.sendToServer(new Network.SaveEntityTemplatePacket(template));
            close();
        });
    }

    @Override
    public String title()
    {
        return "Entity Template";
    }
}
