package com.fantasticsource.weightedspawnzones.blocksanditems;

import com.fantasticsource.nbtmanipulator.NBTManipulator;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

import static com.fantasticsource.weightedspawnzones.WeightedSpawnZones.MODID;

public class ItemEntityEditor extends Item
{
    public ItemEntityEditor()
    {
        setCreativeTab(BlocksAndItems.creativeTab);

        setUnlocalizedName(MODID + ":entityeditor");
        setRegistryName("entityeditor");
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack stack, EntityPlayer player, EntityLivingBase target, EnumHand hand)
    {
        if (player.isSneaking())
        {
            openMainMenu();
            return true;
        }

        //Right click entity
        if (player instanceof EntityPlayerMP && !(target instanceof EntityPlayer)) NBTManipulator.entity((EntityPlayerMP) player, target);
        return true;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand handIn)
    {
        if (player.isSneaking())
        {
            openMainMenu();
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(handIn));
        }

        System.out.println("place entity");
        if (false)
        {
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(handIn));
        }

        return super.onItemRightClick(world, player, handIn);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn)
    {
        tooltip.add(TextFormatting.YELLOW + "Right click entity to edit it, save it as a spawnable entity, or replace it with another entity");
        tooltip.add(TextFormatting.GREEN + "Right click a block position to spawn a copy of the currently selected entity");
        tooltip.add(TextFormatting.BLUE + "Right click while sneaking to access the entity selection screen and other menus");
    }


    protected static void openMainMenu()
    {
        System.out.println("open main entity menu");
    }
}
