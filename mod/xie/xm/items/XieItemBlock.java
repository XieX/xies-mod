package mod.xie.xm.items;


import mod.xie.xm.blocks.IXieBlock;
import mod.xie.xm.util.Xie;
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
import net.minecraft.util.Icon;
import net.minecraft.world.World;

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
	
	@Override
	public Icon getIconFromDamage(int i) {
        return block.getIcon(2, i);
    }

    public int getMetadata(int i) {
        return i;
    }
	
	public String getItemNameIS(ItemStack itemstack) {
		if (xieBlock==null) {
			if (block==null) return "null";
			else return block.getUnlocalizedName();
		} else return xieBlock.getBlockNameIS(itemstack);
	}
}
