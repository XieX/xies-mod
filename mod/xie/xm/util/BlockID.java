package mod.xie.xm.util;

import mod.xie.xm.blocks.XieBlockLeaves;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class BlockID {
	public int blockID;
	public int metadata;
	
	public BlockID() {
		this(-1,-1);
	}
	
	public BlockID(int id) {
		this(id,-1);
	}
	
	public BlockID(int id, int meta) {
		blockID=id;
		metadata=meta;
	}
	
	public boolean matches(World world, int x, int y, int z) {
		int id = world.getBlockId(x, y, z);
		int meta = -1;
		if (hasMeta()) {
			Block b = getBlock();
			if (b instanceof XieBlockLeaves) {
				meta=((XieBlockLeaves) b).metaFilter(world.getBlockMetadata(x,y,z));
			} else {
				meta=world.getBlockMetadata(x, y, z);
			}
		}
		
		return idMatch(id) && metaMatch(meta);
	}
	
	public boolean hasMeta() {
		return metadata>=0;
	}
	
	public boolean idMatch(int id) {
		return id==blockID;
	}
	
	public boolean metaMatch(int meta) {
		return metadata<0 || meta==metadata;
	}
	
	public Block getBlock() {
		if (blockID<0 || blockID>=Block.blocksList.length) return null;
		return Block.blocksList[blockID];
	}

	public boolean isBlock() {
		return blockID>=0;
	}

	public void set(World world, int i, int j, int k) {
		if (hasMeta()) Xie.setBlockAndMeta(world, i, j, k, blockID, metadata);
		else Xie.setBlock(world, i, j, k, blockID);
	}

	public ItemStack getItemStack(int count) {
		if (hasMeta()) return new ItemStack(blockID,count,metadata);
		else return new ItemStack(blockID,count,0);
	}
	
	public ItemStack getItemStack() {
		return getItemStack(1);
	}
}