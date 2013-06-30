package mod.xie.xm.blocks;

import java.util.List;

import mod.xie.xm.tex.XieTex;
import mod.xie.xm.util.Drop;
import mod.xie.xm.util.Droppables;
import mod.xie.xm.util.Xie;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockPane;
import net.minecraft.block.StepSound;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.creativetab.CreativeTabs;
import java.util.Random;


public class XieBlockFence extends XieBlock {

	public XieBlockFence(String n, int id, Material mat) {
		super(id, mat, n);
        this.setCreativeTab(CreativeTabs.tabDecorations);
	}
	
	public boolean isFenceAt(IBlockAccess iblockaccess, int i, int j, int k) {
        int l = iblockaccess.getBlockId(i, j, k);
        if (l == blockID || l == Block.fenceGate.blockID) return true;
        Block block = Block.blocksList[l];
        
        if (block==null) return false;
        
        if (block instanceof BlockFence) return true;

        if (block.blockMaterial.isOpaque() 
        		&& block.renderAsNormalBlock())
        	return block.blockMaterial != Material.pumpkin;
        else return false;
    }
	
  
}
