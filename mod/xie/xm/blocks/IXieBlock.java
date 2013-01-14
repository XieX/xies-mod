package mod.xie.xm.blocks;

import java.util.List;

import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.XieTex;
import net.minecraft.block.Block;
import net.minecraft.block.StepSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;



public interface IXieBlock {
	Block getBlock();
	void setRenderType(int r);
	void addDrop(Drop drop);    
	XieTex getTex();
    void setTex(XieTex t);
    Block setStepSound(StepSound stepsound);
    Block setHardness(float f);
    String getBlockName();
	void setDrops(Droppables drops);
	void setBlockBounds(float[] bounds);
	void setBlockBounds(float[][] blockBounds);
	boolean hasSubTypes();
	String getBlockNameIS(ItemStack itemstack);
	void setNames(String string[]);
	String[] getNames();
	void setBecomesWhenDestroyed(int[] bwd);
	void walkThroughable(boolean b);
	Block setLightValue(float f);
	Block setLightOpacity(int i);
	void setIsOpaque(boolean b);
	void setCanBePlacedOn(List<Block> l);
	void setIsSpikey(boolean b);
	Object getGuiElement(EntityPlayer player, World world, int i, int j, int k);
}
