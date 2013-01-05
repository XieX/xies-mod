package xie.mods.xm.items;

import xie.mods.xm.blocks.IXieBlock;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumToolMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;
import xie.mods.xm.util.Xie;

public class XieItemBlock extends ItemBlock {

	protected IXieBlock xieBlock;
	protected Block block;
	
	public XieItemBlock(int i) {
		super(i);
		block = Block.blocksList[i + Xie.idShift];
		if (block instanceof IXieBlock) xieBlock = (IXieBlock) block;
		setMaxDamage(0);
		setHasSubtypes(true);
	}
	
	public int getIconFromDamage(int i) {
        return block.getBlockTextureFromSideAndMetadata(2, i);
    }

    public int getMetadata(int i) {
        return i;
    }
	
	public String getItemNameIS(ItemStack itemstack) {
		if (xieBlock==null) {
			if (block==null) return "null";
			else return block.getBlockName();
		} else return xieBlock.getBlockNameIS(itemstack);
	}
}
