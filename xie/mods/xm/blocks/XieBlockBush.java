package xie.mods.xm.blocks;

import java.util.Random;

import net.minecraft.world.World;

import xie.mods.xm.util.Xie;


public class XieBlockBush extends XieBlockPlant {

	// constructor
	public XieBlockBush(String plantName, int plantID) {
		super(plantName, plantID);
		setRenderType(1);
		setHardness(0.15F);
	}
	
	public void onBlockDestroyedByPlayer(World world, int i, int j, int k, int l)
	{
	   if(world.isRemote) return;
	   
	   super.onBlockDestroyedByPlayer(world, i, j, k, l);
	   
	   if (l==maxGrowth) {
		   int newMeta = maxGrowth-3;
		   if (newMeta<0) newMeta=0;
		   Xie.setBlockAndMeta(world, i, j, k, this.blockID, newMeta);
	   }
	}
	
	public void updateTick(World world, int i, int j, int k, Random random) {
		if (world.isRemote) return;
		if (world.getBlockMetadata(i,j,k)<=maxGrowth) super.updateTick(world,i,j,k,random);
	}
}
